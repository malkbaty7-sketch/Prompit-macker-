# Forgeprom Code Studio — Android Native Project & Automated APK Build

تطبيق **Forgeprom Code Studio** الأصلي المكتوب بلغة Kotlin و Jetpack Compose مع ميزة البناء الآلي لملف APK عبر **GitHub Actions**.

---

## 🚀 كيفية تنزيل ملف الـ APK وتثبيته على هاتفك عبر GitHub

تم إعداد دورة عمل آلية بالكامل داخل المستودع (`.github/workflows/build-apk.yml`) تقوم ببناء التطبيق بصيغة `APK` موقّع وجاهز للتثبيت على أي هاتف يعمل بنظام أندرويد (Android 8.0 Oreo فما فوق).

### الخطوات البسيطة:

### 1. رفع المستودع إلى حسابك على GitHub
إذا لم تكن قد قمت برفع المستودع بعد، قم بإنشاء مستودع جديد على GitHub ونفّذ الأوامر التالية من المجلد الرئيسي:
```bash
git init
git add .
git commit -m "feat: initial commit of Forgeprom Code Studio Android Native"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

### 2. التوجه إلى تبويب Actions في GitHub
بمجرد عمل `push` إلى فرع `main`:
1. افتح صفحة المستودع الخاص بك على GitHub في المتصفح.
2. اضغط على تبويب **Actions** في الشريط العلوي.
3. ستشاهد سير العمل (Workflow) المسمى:  
   **"Build Android APK (Forgeprom Code Studio)"** قيد التشغيل تلقائياً 🔄.
4. يستغرق البناء عادة ما بين دقيقتين إلى 3 دقائق.

### 3. تنزيل ملف الـ APK المباشر
1. بعد اكتمال البناء وظهور علامة الصح الخضراء ✅، اضغط على اسم العملية الأخيرة.
2. انزل إلى قسم **Artifacts** في أسفل الصفحة.
3. ستجد ملفاً باسم:  
   📦 **`Forgeprom-CodeStudio-Debug-APK`**
4. اضغط عليه لتنزيل ملف الـ ZIP الذي يحتوي على:  
   `app-debug.apk`

### 4. تثبيت التطبيق على هاتفك المحمول
1. انقل ملف `app-debug.apk` إلى هاتفك (أو حمّله مباشرة من متصفح الهاتف عبر الدخول إلى GitHub).
2. افتح الملف واضغط **تثبيت (Install)**.
3. إذا طلب الهاتف إذناً لتثبيت التطبيقات من مصادر غير معروفة، وافق على الإذن (معتاد في جميع ملفات APK التي يتم بناؤها للتطوير والاختبار الشخصي).
4. استمتع بتجربة تطبيق **Forgeprom Code Studio** بأداء أصلي فائق وبواجهة عربية كاملة 100% دون إنترنت!

---

## 🛠️ مواصفات المشروع التقنية (Tech Stack)

- **اللغة:** Kotlin 2.0.21
- **الواجهة:** Jetpack Compose + Material Design 3
- **الاتجاه:** RTL للغة العربية افتراضياً مع معالجة الأكواد واللغات المختلطة (LTR)
- **قاعدة البيانات:** Room Database (تخزين محلي Local-First 100%)
- **إدارة الذاكرة:** فاحص عتاد برمجي `HardwareProfiler` لحماية الهواتف المتوسطة (4GB - 8GB RAM) من مشاكل الـ OOM
- **الأمان:** فحص البصمة الرقمية `SHA-256` لملفات النماذج
- **البناء:** Gradle 8.10.2 + Android Gradle Plugin 8.7.2 + JDK 17
- **الحد الأدنى للنظام:** MinSDK 26 (Android 8.0) | TargetSDK 35 (Android 15)

---

## 💻 البناء المحلي عبر سطر الأوامر (اختياري)
إذا كنت تفضل بناء ملف الـ APK على حاسوبك الشخصي باستخدام Android Studio أو سطر الأوامر:
```bash
./gradlew assembleDebug
```
ستجد الملف الناتج في المسار:
`app/build/outputs/apk/debug/app-debug.apk`
