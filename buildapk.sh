#!/bin/bash
set -e
cd /root/avium
export JAVA_HOME=/root/avium/prebuilts/jdk/jdk21/linux-x86
export PATH=$JAVA_HOME/bin:$PATH
SRC=/mnt/f/wsl/nfcreader
OUT=/root/nfcout
AJAR=/root/avium/prebuilts/sdk/current/public/android.jar
JAVAC=$JAVA_HOME/bin/javac
AAPT2=/root/avium/out/host/linux-x86/bin/aapt2
D8=/root/avium/out/host/linux-x86/bin/d8
ZIPALIGN=/root/avium/out/host/linux-x86/bin/zipalign
APKSIGNER=/root/avium/out/host/linux-x86/bin/apksigner

rm -rf $OUT
mkdir -p $OUT/compiled $OUT/classes $OUT/dex $OUT/gen

echo "== 1 aapt2 compile =="
$AAPT2 compile --dir $SRC/res -o $OUT/compiled/

echo "== 2 aapt2 link =="
$AAPT2 link -o $OUT/base.apk -I $AJAR \
  --manifest $SRC/AndroidManifest.xml --java $OUT/gen \
  --min-sdk-version 31 --target-sdk-version 36 \
  $OUT/compiled/*.flat
echo LINK_OK

echo "== 3 javac =="
$JAVAC -source 11 -target 11 -encoding UTF-8 -nowarn -Xlint:-options \
  -classpath $AJAR -d $OUT/classes \
  $SRC/src/org/fotlim/nfcreader/MainActivity.java \
  $OUT/gen/org/fotlim/nfcreader/R.java
echo JAVAC_OK
find $OUT/classes -name '*.class' | wc -l

echo "== 4 d8 =="
find $OUT/classes -name '*.class' > $OUT/cls.txt
$D8 --release --min-api 31 --lib $AJAR --output $OUT/dex $(cat $OUT/cls.txt)
ls -la $OUT/dex

echo "== 5 package =="
cd $OUT && cp base.apk unaligned.apk
cd $OUT/dex && zip -q -j $OUT/unaligned.apk classes.dex
cd $OUT && unzip -l unaligned.apk

echo "== 6 zipalign =="
$ZIPALIGN -f -p 4 unaligned.apk aligned.apk
echo ALIGN_OK

echo "== 7 sign =="
KS=/root/nfckey.jks
$JAVA_HOME/bin/keytool -genkeypair -keystore $KS -storepass android -keypass android \
  -alias testkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=NFC Reader, O=fotlim, C=CN" > /dev/null 2>&1 || true
$APKSIGNER sign --ks $KS --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias testkey --out nfc-reader.apk aligned.apk
ls -la nfc-reader.apk
$APKSIGNER verify --print-certs nfc-reader.apk 2>&1 | head -4
echo "===== ALL DONE ====="
