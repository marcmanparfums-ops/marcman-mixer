# 📋 Ghid Import Date IFRA Transparency List

## 🎯 Despre IFRA Transparency List

[IFRA Transparency List](https://acc.ifrafragrance.org/transparency-list?utm_source=chatgpt.com) este lista oficială a **International Fragrance Association** care conține **peste 3600+ ingrediente** validate și aprobate pentru industria parfumurilor.

### Ce conține lista:
- **CAS Number** (Chemical Abstracts Service) - identificator unic chimic
- **Nume principal** al ingredientului
- **Categorie NCS** (Naturals Category System) - pentru ingrediente naturale
- **Status IFRA** - aprobat, restricționat, etc.

---

## 📦 Fișiere Incluse

### 1. `ifra_ingredients_sample.csv`
- **50 ingrediente** reprezentative din lista IFRA
- Selecție curată cu ingrediente populare:
  - **Sintetic**: Linalool, Geraniol, Vanillin, Galaxolide
  - **Natural**: Myrrh oil, Lemon oil, Orange oil, Rosemary oil
  - **Essential Oils**: Eucalyptus, Clove, Peppermint
  - **Carrier Oils**: Sunflower, Almond

### Format CSV:
```csv
cas_number,name,ifra_naturals_category,category,description
2306-78-7,Nerolidyl acetate,,Essential Oils,Sweet floral woody note...
8016-37-3,Myrrh oil,K2.12,Natural Extracts,Ancient aromatic resin...
```

---

## 🚀 Cum să Importezi Datele

### Metoda 1: Programatic (din cod Java)

```java
DatabaseManager dbManager = new DatabaseManager();
IngredientRepository repository = new IngredientRepositoryImpl(dbManager);
IfraDataImporter importer = new IfraDataImporter(repository);

int imported = importer.importFromCsv("data/ifra_ingredients_sample.csv");
System.out.println("Imported " + imported + " ingredients");
```

### Metoda 2: Command Line

```bash
cd c:\Users\Marcman\Documents\MarcmanMixer
mvn exec:java -Dexec.mainClass="ro.marcman.mixer.sqlite.IfraDataImporter" \
              -Dexec.args="data/ifra_ingredients_sample.csv" \
              -pl sqlite
```

### Metoda 3: Din UI (viitor)

În aplicația JavaFX va fi un buton "Import IFRA Data" în tab Ingredients.

---

## 📝 Structura Ingredientelor

### Câmpuri IFRA:
| Câmp | Descriere | Exemplu |
|------|-----------|---------|
| `casNumber` | Identificator chimic unic | `8016-37-3` |
| `name` | Nume principal | `Myrrh oil` |
| `ifraNaturalsCategory` | Categorie NCS | `K2.12` |
| `ifraStatus` | Status (aprobat/restricționat) | `IFRA Approved` |
| `category` | Categorie generală | `Natural Extracts` |
| `description` | Descriere olfactivă | `Warm balsamic...` |

### Câmpuri Arduino (setate manual):
- `arduinoUid` - UID SLAVE (ex: `0x12345678`)
- `arduinoPin` - Pin 0-69
- `defaultDuration` - Durată impuls în ms

### Câmpuri Business:
- `stockQuantity` - Cantitate stoc
- `costPerUnit` - Cost per unitate
- `supplier` - Furnizor
- `batchNumber` - Număr lot

---

## 🔍 Categorii IFRA Naturals (NCS)

### Exemplu categorii din listă:

| Cod | Descriere |
|-----|-----------|
| **G2.x** | Citrus oils (G2.5=Mandarin, G2.20=Orange, G2.30=Lemon) |
| **H2.x** | Seed oils (H2.50=Almond) |
| **J2.x** | Leaf oils (J2.15=Rosemary, J2.17=Peppermint, J2.20=Clove) |
| **K2.x** | Balsam oils (K2.9=Peru, K2.12=Myrrh) |
| **F2.x** | Vegetable oils (F2.12=Sunflower, F2.13=Jasmine) |

---

## 🎨 Ingrediente Populare Incluse

### Top 10 Sintetic:
1. **Linalool** (78-70-6) - Fresh floral lavender
2. **Geraniol** (106-24-1) - Rosy floral
3. **Vanillin** (121-33-5) - Classic vanilla
4. **Galaxolide** (1222-05-5) - Clean musk
5. **Dihydromyrcenol** (13171-00-1) - Fresh lime
6. **Hexyl cinnamaldehyde** (101-86-0) - Sweet jasmine
7. **Citral** (5392-40-5) - Strong lemon
8. **Benzyl acetate** (140-11-4) - Fruity jasmine pear
9. **Piperonal** (120-57-0) - Sweet vanilla heliotrope
10. **d-Limonene** (5989-27-5) - Fresh citrus orange

### Top Natural:
1. **Orange oil** (8008-57-9) - Sweet fresh orange G2.20
2. **Peppermint oil** (8006-90-4) - Cool minty J2.17
3. **Rosemary oil** (8000-25-7) - Fresh herbaceous J2.15
4. **Myrrh oil** (8016-37-3) - Warm balsamic K2.12
5. **Clove oil** (8000-34-8) - Spicy warm J2.20

---

## 📊 Database Schema

```sql
CREATE TABLE ingredients (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT,
    
    -- IFRA data
    cas_number TEXT UNIQUE,
    ifra_naturals_category TEXT,
    ifra_status TEXT,
    
    -- Arduino control
    arduino_uid TEXT,
    arduino_pin INTEGER,
    default_duration INTEGER,
    
    -- Physical properties
    concentration REAL,
    unit TEXT,
    cost_per_unit REAL,
    stock_quantity REAL,
    
    -- Supplier
    supplier TEXT,
    batch_number TEXT,
    
    active INTEGER DEFAULT 1
);
```

---

## 🌐 Extragere Date Complete de pe IFRA

Pentru a obține toate cele **3600+ ingrediente**:

### Opțiune 1: Web Scraping (Python)

```python
import requests
from bs4 import BeautifulSoup
import csv

base_url = "https://acc.ifrafragrance.org/transparency-list"

# IFRA are 151 pagini, fiecare cu ~25 ingrediente
for page in range(1, 152):
    url = f"{base_url}?page={page}"
    # Extrage tabelul și salvează în CSV
    # ... (cod de scraping)
```

### Opțiune 2: Manual Export

1. Accesează: https://acc.ifrafragrance.org/transparency-list
2. Navighează prin toate cele 151 pagini
3. Copy-paste în Excel/CSV
4. Salvează ca `ifra_ingredients_full.csv`

### Opțiune 3: API (dacă există)

IFRA poate oferi export CSV sau API - contactează-i pentru acces.

---

## ✅ Verificare Import

După import, verifică în aplicație:

```sql
-- Total ingrediente
SELECT COUNT(*) FROM ingredients WHERE active = 1;

-- Ingrediente cu CAS number
SELECT COUNT(*) FROM ingredients WHERE cas_number IS NOT NULL;

-- Ingrediente naturale (cu categorie NCS)
SELECT COUNT(*) FROM ingredients WHERE ifra_naturals_category IS NOT NULL;

-- Top 10 categorii
SELECT category, COUNT(*) as count 
FROM ingredients 
WHERE active = 1 
GROUP BY category 
ORDER BY count DESC 
LIMIT 10;
```

---

## 🎯 Next Steps

După import:

1. **Asociază Arduino** - Setează `arduinoUid` și `arduinoPin` pentru fiecare ingredient
2. **Setează stock** - Introdu `stockQuantity` pentru inventar
3. **Adaugă costuri** - Completează `costPerUnit` pentru tracking financiar
4. **Creează rețete** - Folosește ingredientele în tab Recipes

---

## 📚 Resurse

- **IFRA Official**: https://ifrafragrance.org/
- **Transparency List**: https://acc.ifrafragrance.org/transparency-list
- **IFRA Standards**: https://ifrafragrance.org/standards
- **CAS Registry**: https://www.cas.org/

---

## ⚠️ Note Importante

1. **CAS Number = Unique**: Un singur ingredient per CAS number
2. **IFRA Status**: Verifică restricții înainte de utilizare
3. **Allergens**: Unele ingrediente necesită declarare pe etichetă
4. **Safety**: Respectă concentrațiile maxime IFRA Standards

---

**Sample data source**: [IFRA Transparency List](https://acc.ifrafragrance.org/transparency-list?utm_source=chatgpt.com)  
**Last updated**: October 2024  
**Total ingredients in sample**: 50  
**Full IFRA list**: 3600+ ingredients



