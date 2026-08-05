# إعداد تحديثات Masarifi Pro عبر GitHub

## المستودع المرتبط

- المالك: `mohammdalloush175-sys`
- المستودع: `MasarifiPro`
- صفحة الإصدارات: `https://github.com/mohammdalloush175-sys/MasarifiPro/releases`

## قاعدة تسمية الإصدارات

يجب أن يكون Tag كل إصدار بصيغة رقمية واضحة، مثل:

- `v1.2.0`
- `v1.2.1`
- `v1.3.0`

يجب إرفاق APK باسم ثابت داخل كل GitHub Release:

`MasarifiPro.apk`

التطبيق يفضّل هذا الاسم، لكنه يقبل أول ملف ينتهي بـ `.apk` إذا لم يجده.

## كيف يعمل الفحص

- فحص فوري عند تشغيل التطبيق.
- فحص دوري كل 6 ساعات باستخدام WorkManager عند توفر الإنترنت.
- الفحص الدوري ليس لحظياً، لأن أندرويد قد يؤخر التنفيذ حسب البطارية وقيود الخلفية.
- لا يعمل الفحص أثناء Force Stop إلى أن يفتح المستخدم التطبيق مرة أخرى.
- يُرسل إشعار واحد فقط لكل Tag جديد.

## نشر الإصدار 1.2.0

بعد بناء النسخة الموقعة:

```bash
cp app/build/outputs/apk/release/MasarifiPro-v1.2.0-release-signed.apk /tmp/MasarifiPro.apk

gh release create v1.2.0 /tmp/MasarifiPro.apk \
  --repo mohammdalloush175-sys/MasarifiPro \
  --title "Masarifi Pro 1.2.0" \
  --notes-file CHANGELOG.md
```

بعد نشر الإصدار، شاشة التحديثات في التطبيق ستعرضه. الإشعارات التلقائية ستفيد مستخدمي 1.2.0 وما بعده عند نشر نسخة أحدث مثل 1.2.1.
