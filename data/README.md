# 📁 Data Directory - MarcmanMixer

## 📋 Conținut

### `ifra_ingredients_sample.csv`
**50 ingrediente** reprezentative din [IFRA Transparency List](https://acc.ifrafragrance.org/transparency-list)

**Format**: CAS number, Name, IFRA Naturals Category, Category, Description

**Categorii incluse:**
- **Sintetic** (35 ingrediente): Linalool, Vanillin, Geraniol, Galaxolide, Limonene, etc.
- **Natural Extracts** (10 ingrediente): Myrrh oil, Orange oil, Clove oil, Rosemary oil, etc.
- **Natural Oils** (5 ingrediente): Sunflower oil, Almond oil, etc.

---

## 🚀 Import Rapid

### Metoda 1: Command Line
```bash
cd c:\Users\Marcman\Documents\MarcmanMixer
mvn exec:java -Dexec.mainClass="ro.marcman.mixer.sqlite.IfraDataImporter" ^
              -Dexec.args="data/ifra_ingredients_sample.csv" ^
              -pl sqlite
```

### Metoda 2: Java Code
```java
DatabaseManager db = new DatabaseManager();
IngredientRepository repo = new IngredientRepositoryImpl(db);
IfraDataImporter importer = new IfraDataImporter(repo);

int imported = importer.importFromCsv("data/ifra_ingredients_sample.csv");
System.out.println("Imported: " + imported + " ingredients");
```

---

## 📊 Sample Data Overview

| Ingredient | CAS Number | Category | IFRA NCS | Description |
|------------|------------|----------|----------|-------------|
| Linalool | 78-70-6 | Synthetic | - | Fresh floral lavender |
| Myrrh oil | 8016-37-3 | Natural | K2.12 | Warm balsamic |
| Orange oil | 8008-57-9 | Natural | G2.20 | Sweet citrus |
| Vanillin | 121-33-5 | Synthetic | - | Classic vanilla |
| Geraniol | 106-24-1 | Synthetic | - | Rosy floral |

**Total**: 50 ingrediente validate IFRA

---

## 🌐 Lista Completă IFRA

Pentru acces la toate **3600+ ingrediente**:

1. **Web Scraping**: Extrage din https://acc.ifrafragrance.org/transparency-list (151 pagini)
2. **Manual Export**: Copy-paste din toate paginile în CSV
3. **Contact IFRA**: Cere export oficial sau API access

Vezi: `IFRA_IMPORT_GUIDE.md` pentru detalii complete

---

## 📝 Format CSV

```csv
cas_number,name,ifra_naturals_category,category,description
2306-78-7,Nerolidyl acetate (isomer unspecified),,Essential Oils,Sweet floral woody note...
8016-37-3,Myrrh oil,K2.12,Natural Extracts,Ancient aromatic resin...
```

**Câmpuri:**
- `cas_number` - CAS Registry Number (UNIQUE)
- `name` - Nume oficial IFRA
- `ifra_naturals_category` - Cod NCS (ex: K2.12, G2.30) sau gol pentru sintetic
- `category` - Categorie generală (Essential Oils, Natural Extracts, Synthetic)
- `description` - Descriere olfactivă

---

## 🔍 Categorii IFRA Naturals (NCS)

| Prefix | Tip | Exemple din Sample |
|--------|-----|-------------------|
| **G2.x** | Citrus oils | G2.5, G2.20, G2.30 |
| **H2.x** | Seed oils | H2.50 |
| **J2.x** | Leaf oils | J2.15, J2.17, J2.20 |
| **K2.x** | Balsam/Resin | K2.9, K2.12 |
| **F2.x** | Vegetable oils | F2.12, F2.13 |

---

## ⚠️ Note Importante

1. **Duplicate CAS**: Import-ul va sări peste CAS numbers duplicate
2. **Validare**: Toate ingredientele sunt validate conform IFRA Standards
3. **Safety**: Verifică restricții IFRA înainte de utilizare
4. **Allergens**: Unele ingrediente necesită declarare pe etichetă

---

## 📚 Documentație

- **IFRA_IMPORT_GUIDE.md** - Ghid detaliat de import
- **../IFRA_INTEGRATION.md** - Documentație integrare completă
- **../README.md** - Documentație tehnică generală

---

## 🎯 Next Steps

După import:
1. Asociază Arduino UID + PIN pentru fiecare ingredient
2. Setează stock quantities
3. Adaugă cost per unit
4. Creează rețete cu ingredientele importate

---

**Data Source**: [IFRA Transparency List](https://acc.ifrafragrance.org/transparency-list)  
**Last Updated**: October 2024  
**Ingredients in Sample**: 50  
**Full IFRA List**: 3600+



