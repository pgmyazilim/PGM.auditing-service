# GÖREV: AAA Veritabanı v2 Şema Değişikliklerine Uygulama Adaptasyonu

Sen bu depodaki uygulamayı, merkezi **AAA** (SSO / yetkilendirme / audit) SQL Server veritabanının **v2 şemasına** uyarlamakla görevlisin. Aşağıdaki değişiklik listesi kesindir; şemayı tahmin etme, bu dokümanı tek doğruluk kaynağı kabul et. Entity/DTO/repository/SQL sorgusu/mapping ne varsa tara ve etkilenen her yeri güncelle.

## 1. Yeniden adlandırmalar (breaking)

| Eski | Yeni | Kapsam |
|---|---|---|
| `RowVersionUtc` (tüm tablolarda) | `ModifiedAtUtc` | kolon adı |
| `GroupActionPermission.GroupOperationPermissionId` | `GroupActionPermissionId` | PK kolonu |
| `RecordAudits.OperationLogId` | `ActionLogId` | kolon adı |
| `Clients.RedirectUri` | KALDIRILDI → `aaa.ClientRedirectUris` tablosu | tablo değişimi |
| `DatabaseCredentials.Password` | KALDIRILDI → `PasswordEncrypted varbinary(512)` | kolon değişimi |

Tüm `Operation*` isimli constraint/FK'lar `Action*` olarak yeniden adlandırıldı; yalnızca constraint adına referans veren kod (ör. hata mesajı parse eden constraint-adı eşlemeleri) varsa güncelle.

## 2. Optimistic concurrency modeli değişti

- Her tabloya SQL Server native `RowVersion` (`rowversion` / `binary(8)`) kolonu eklendi. **Concurrency token artık bu kolondur.**
- Hibernate/JPA: `@Version` alanını `ModifiedAtUtc` yerine `RowVersion`'a taşı (`byte[]`, `@Column(insertable=false, updatable=false)` + `@Version` uygun sürücü desteğiyle; desteklenmiyorsa `WHERE RowVersion = ?` ile manuel kontrol).
- `ModifiedAtUtc` artık sadece bilgilendirme amaçlı "son değişiklik zamanı"dır; uygulama update sırasında `sysutcdatetime()`/`Instant.now()` ile set etmeye devam etsin ama çakışma kontrolünde kullanmasın.
- Çoğu tabloya `CreatedAtUtc datetime2(7) NOT NULL DEFAULT sysutcdatetime()` eklendi. Insert'lerde göndermesen de olur (DB default), entity'lere read-only alan olarak ekle.

## 3. Users tablosu

- `PasswordHash` → `nvarchar(200)`. **Parola algoritması değişiyor:** tuzsuz SHA-256 yerine **bcrypt** (veya argon2id) kullan; hash string'i salt+cost içerir, ayrı salt kolonu yok. `PasswordHashLegacy` yalnızca eski hash doğrulaması ve ilk girişte re-hash için kullanılabilir.
- Yeni alanlar:
  - `FailedLoginCount int NOT NULL DEFAULT 0` — her başarısız girişte artır, başarılı girişte sıfırla.
  - `LockedUntilUtc datetime2 NULL` — eşik aşılınca (öneri: 5 deneme) set et; login akışında `LockedUntilUtc > now` ise girişi reddet.
  - `OtpSecretEncrypted varbinary(256)` ve `OtpRecoveryCodesEncrypted varbinary(max)` — TOTP secret ve kurtarma kodları **uygulama tarafında şifrelenmiş** saklanır (AES-GCM, anahtar config/KMS'ten; asla düz metin yazma).
- Telefon kolonları (`PhoneHome`, `PhoneOffice`, `PhoneFax`, `PhoneMobile`) `bigint` → `nvarchar(25)`. Entity tiplerini `String` yap; `+` ve baştaki sıfırlar korunur. `PhoneOfficeExt` `int` kaldı.
- `(EmailUser, EmailDomain)` üzerinde filtreli unique index var: aynı e-posta iki kullanıcıya yazılamaz; duplicate key hatasını kullanıcı dostu mesaja çevir.

## 4. Clients / SSO client modeli

- Yeni kolonlar: `ClientSecretHash nvarchar(200)` (secret'ı bcrypt ile hash'le, düz saklama yok), `IsActive bit` (yetki kontrollerinde pasif client reddedilmeli), `AllowedGrantTypes nvarchar(200)` (virgülle ayrık liste), `AccessTokenLifetimeSeconds` (default 3600), `RefreshTokenLifetimeSeconds` (default 1209600).
- Redirect URI'ler artık `aaa.ClientRedirectUris (ClientRedirectUriId, ClientId, RedirectUri)` tablosunda, `(ClientId, RedirectUri)` unique. Redirect doğrulaması **tam eşleşme** ile bu tabloya karşı yapılmalı.
- `Clients.Name` artık unique.

## 5. Sessions

- Yeni kolonlar: `ClientId int NULL` (FK → Clients; oturum açılırken hangi client'tan geldiği yazılmalı), `ExpiresAtUtc`, `LastActivityUtc`.
- Oturum doğrulamasında `IsOpen = 1` yeterli değil: `ExpiresAtUtc` geçmişse oturumu geçersiz say ve kapat. Her doğrulanan istekte (veya makul aralıkla) `LastActivityUtc` güncelle; sliding expiration isteniyorsa `ExpiresAtUtc`'yi de kaydır.
- Süresi dolmuş açık oturumları kapatan periyodik bir temizlik job'ı ekle (`IsOpen=1 AND ExpiresAtUtc < now` → `IsOpen=0, IsNormalClose=0, ClosedAtUtc=now`).

## 6. ActionLogs

- Yeni kolon: `ActorUserId int NULL` (FK → Users). **Her log kaydında, kullanıcı biliniyorsa doğrudan doldur** — SessionId üzerinden dolaylı çözümlemeye güvenme. Başarısız login denemelerinde SessionId NULL olsa bile hedef kullanıcı bulunabiliyorsa ActorUserId yazılmalı.

## 7. GroupActionPermission

- PK kolon adı `GroupActionPermissionId` oldu (bkz. bölüm 1).
- Yeni kolon: `UsedExecutionCount smallint NOT NULL DEFAULT 0`. `AllowedExecutionCount` doluysa yetki kontrolü `UsedExecutionCount < AllowedExecutionCount` şartını içermeli ve her izinli çalıştırmada sayaç **atomik** artırılmalı, örn.:
  ```sql
  UPDATE aaa.GroupActionPermission
     SET UsedExecutionCount = UsedExecutionCount + 1
   WHERE GroupActionPermissionId = @id
     AND (AllowedExecutionCount IS NULL OR UsedExecutionCount < AllowedExecutionCount);
  ```
  Etkilenen satır 0 ise izin reddedilir. `ExpiresAtUtc` kontrolü de aynı sorguya eklenebilir.

## 8. DatabaseCredentials

- `Password` yok; `PasswordEncrypted varbinary(512)` var. Bağlantı şifreleri uygulama tarafında AES-GCM ile şifrelenip yazılır, okunurken çözülür. Şifreleme anahtarı koddan/DB'den değil, güvenli config/KMS'ten gelmeli. Mevcut düz metin şifreler geçersiz — dev ortamında yeniden girilmeleri gerekiyor.

## 9. Tekillik ve bütünlük — hata yakalama

Aşağıdaki yeni unique kısıtlar duplicate insert'te 2601/2627 hatası üretir; ilgili servislerde anlamlı hata dönüşü ekle:
- `ClientModules (ClientId, ModuleId)` unique + artık gerçek FK'lı.
- `SettingValues (SettingId, UserId)` ve `(SettingId, UserGroupId)` filtreli unique — aynı ayar aynı hedefe iki kez yazılamaz; "upsert" mantığına geç.
- `ModulesDatabases.ModuleId` artık NOT NULL; `(ModuleId, DatabaseAlias)` alias doluysa unique.
- `ActionConstraintGroupValue.ValueLogicalOperator` yalnızca `AND`/`OR`/NULL olabilir (CHECK).

## 10. Yapma / dikkat et

- Şemaya kendi başına kolon/tablo ekleme-çıkarma yapma; yalnızca uygulama kodunu uyarl.
- SQL string'lerinde, JPA `@Column(name=...)` mapping'lerinde, DTO/JSON alan adlarında ve Flutter tarafındaki modellerde eski adları (`RowVersionUtc`, `OperationLogId`, `GroupOperationPermissionId`, `Clients.RedirectUri`, `DatabaseCredentials.Password`) grep ile ara; hiçbiri kalmamalı.
- Tüm zaman alanları UTC'dir; local time yazma.
- Değişiklik sonrası: derleme + mevcut testler + login/oturum/permission akışları için entegrasyon testi. `PasswordHashLegacy` fallback'ı test kapsamına al.

Çalışmaya, depoda yukarıdaki eski adları geçen tüm dosyaları listeleyerek başla; sonra tablo bazında ilerle ve her bölüm için yaptığın değişiklikleri özetle.
