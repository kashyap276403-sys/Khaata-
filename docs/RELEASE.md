# Signed release guide (phone se bhi ho sakta hai)

Debug APK (Actions -> artifact) sirf testing ke liye hai. Asli release ke liye
ek **keystore** chahiye. Keystore kho gaya = app update nahi hoga. Isko 2 jagah
safe rakhein (e.g. Google Drive private + email to self). **Kabhi repo me commit
na karein, kisi ko na dein.**

## 1. Keystore banana (ek baar)
Kisi computer/Termux par (Java chahiye):

    keytool -genkeypair -v -keystore release.jks -alias aurax \
      -keyalg RSA -keysize 2048 -validity 10000

Passwords yaad rakhein / likh kar rakhein.

## 2. Base64 banana
    base64 -w0 release.jks > release.b64

## 3. GitHub Secrets (Repo -> Settings -> Secrets and variables -> Actions)
| Secret | Value |
|---|---|
| ANDROID_KEYSTORE_BASE64 | release.b64 ka poora text |
| ANDROID_KEYSTORE_PASSWORD | keystore password |
| ANDROID_KEY_ALIAS | aurax |
| ANDROID_KEY_PASSWORD | key password |

## 4. Release nikalna
GitHub -> Releases -> "Draft a new release" -> naya tag (e.g. `v0.6.0`) ->
Publish. Workflow analyze + tests chalata hai, signed APK banata hai, verify
karta hai ki debug key nahi hai, aur Release page par APK laga deta hai.
Agar secrets missing hain to build fail hota hai (debug-signed APK kabhi
release nahi hota).

## Status
Workflow likha gaya hai lekin **abhi chalaya nahi gaya** (keystore/secrets
user ke paas hain). Pehli release par errors aa sakte hain; log bhej dein.
