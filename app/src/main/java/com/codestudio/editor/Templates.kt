package com.codestudio.editor

/**
 * قالب‌های آماده برای ساخت پروژه جدید — اسکلت کامل HTML5 با لینک Bootstrap CDN
 */
object Templates {

    fun indexHtml(projectName: String): String = """<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$projectName</title>
  <!-- Bootstrap 5 CDN -->
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <!-- استایل پروژه -->
  <link rel="stylesheet" href="style.css">
</head>
<body>

  <div class="container py-5">
    <div class="text-center">
      <h1 class="text-primary fw-bold">سلام دنیا!</h1>
      <p class="lead text-muted">پروژه شما در CodeStudio آماده است.</p>
      <button class="btn btn-primary" type="button">دکمه بوت‌استرپ</button>
    </div>

    <div class="row mt-5 g-3">
      <div class="col-md-4">
        <div class="card h-100 shadow-sm">
          <div class="card-body">
            <h5 class="card-title">کارت اول</h5>
            <p class="card-text">این یک نمونه کارت Bootstrap است.</p>
            <a href="#" class="btn btn-outline-primary btn-sm">بیشتر</a>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card h-100 shadow-sm">
          <div class="card-body">
            <h5 class="card-title">کارت دوم</h5>
            <p class="card-text">با کلاس‌های Bootstrap صفحه را زیبا کنید.</p>
            <a href="#" class="btn btn-outline-success btn-sm">بیشتر</a>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card h-100 shadow-sm">
          <div class="card-body">
            <h5 class="card-title">کارت سوم</h5>
            <p class="card-text">از دکمه Run برای دیدن پیش‌نمایش استفاده کنید.</p>
            <a href="#" class="btn btn-outline-info btn-sm">بیشتر</a>
          </div>
        </div>
      </div>
    </div>
  </div>

  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
  <script src="script.js"></script>
</body>
</html>"""

    fun styleCss(): String = """/* استایل پروژه شما */
body {
  font-family: Vazirmatn, Tahoma, sans-serif;
}

.hero {
  background: linear-gradient(135deg, #0d6efd22, #6f42c122);
  border-radius: 12px;
}

/* ... */
"""

    fun scriptJs(): String = """// جاوااسکریپت پروژه شما
document.addEventListener('DOMContentLoaded', function () {
  console.log('پروژه CodeStudio بارگذاری شد ✔');

  var btn = document.querySelector('.btn');
  if (btn) {
    btn.addEventListener('click', function () {
      alert('سلام از CodeStudio!');
    });
  }
});
"""
}
