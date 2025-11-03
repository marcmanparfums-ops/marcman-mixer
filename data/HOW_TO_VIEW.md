# 👀 Cum să Vizualizezi Ingredientele IFRA Sample

## 🎯 Quick Reference

| Metoda | Dificultate | Aspect | Interactivitate |
|--------|-------------|--------|-----------------|
| **HTML Browser** | ⭐ Foarte ușor | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Excel/LibreOffice** | ⭐ Foarte ușor | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **PowerShell Table** | ⭐⭐ Ușor | ⭐⭐⭐ | ⭐⭐ |
| **Notepad/VSCode** | ⭐ Foarte ușor | ⭐⭐ | ⭐ |

---

## 🌐 Metoda 1: HTML Interactiv (RECOMANDAT) ⭐

**Cel mai frumos și interactiv!**

```powershell
# Dublu-click pe:
c:\Users\Marcman\Documents\MarcmanMixer\data\view_ingredients.html

# SAU din PowerShell:
Start-Process "c:\Users\Marcman\Documents\MarcmanMixer\data\view_ingredients.html"
```

**Caracteristici:**
- ✅ Design modern cu gradient violet
- ✅ Căutare în timp real (tastează pentru a filtra)
- ✅ Butoane de filtrare (Toate/Sintetic/Natural/NCS)
- ✅ Statistici live (Total/Sintetic/Natural)
- ✅ Culori pe categorii
- ✅ Badge-uri pentru categorii IFRA NCS
- ✅ Responsive (funcționează pe orice ecran)

---

## 📊 Metoda 2: Excel (PENTRU EDITARE)

**Pentru editare și sortare avansată:**

```powershell
# Deschide direct în Excel
Start-Process excel "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv"

# SAU în LibreOffice Calc
Start-Process calc "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv"
```

**Avantaje:**
- ✅ Editare ușoară
- ✅ Sortare pe orice coloană
- ✅ Filtre Excel native
- ✅ Export în alte formate
- ✅ Grafice și pivot tables

---

## 💻 Metoda 3: PowerShell - Simplu

**Lista completă:**
```powershell
cd "c:\Users\Marcman\Documents\MarcmanMixer\data"
Import-Csv ifra_ingredients_sample.csv | Format-Table -AutoSize
```

**Top 10:**
```powershell
Import-Csv ifra_ingredients_sample.csv | Select-Object -First 10 | Format-Table
```

**Doar naturale cu NCS:**
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.ifra_naturals_category -ne ''} | 
    Format-Table cas_number,name,ifra_naturals_category
```

**Căutare (ex: "Linalool"):**
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.name -like '*Linalool*'} | 
    Format-Table
```

**Statistici pe categorii:**
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Group-Object category | 
    Sort-Object Count -Descending | 
    Format-Table Name,Count
```

---

## 📝 Metoda 4: Notepad/Text Editor

**Simplu text:**
```powershell
notepad "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv"
```

**Visual Studio Code (dacă instalat):**
```powershell
code "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv"
```

---

## 🔍 Query-uri PowerShell Utile

### Top Ingrediente Sintetice Populare
```powershell
cd "c:\Users\Marcman\Documents\MarcmanMixer\data"
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.category -eq 'Synthetic'} | 
    Select-Object -First 15 name,cas_number,description | 
    Format-Table -Wrap
```

### Toate Uleiurile Naturale
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.name -like '*oil*'} | 
    Format-Table name,cas_number,ifra_naturals_category
```

### Ingrediente Citrice (categoria G2)
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.ifra_naturals_category -like 'G2.*'} | 
    Format-Table name,ifra_naturals_category,description
```

### Count pe Categorii IFRA NCS
```powershell
Import-Csv ifra_ingredients_sample.csv | 
    Where-Object {$_.ifra_naturals_category -ne ''} | 
    Group-Object ifra_naturals_category | 
    Sort-Object Name | 
    Format-Table Name,Count
```

---

## 📱 Quick View Commands

**Salvează aceste comenzi pentru uz rapid:**

```powershell
# ALIAS 1: View all
function Show-IFRA { 
    Import-Csv "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv" | 
    Out-GridView -Title "IFRA Sample Ingredients"
}

# ALIAS 2: Search
function Search-IFRA($term) {
    Import-Csv "c:\Users\Marcman\Documents\MarcmanMixer\data\ifra_ingredients_sample.csv" | 
    Where-Object {$_.name -like "*$term*" -or $_.description -like "*$term*"} | 
    Format-Table name,cas_number,category,description -AutoSize
}

# Utilizare:
Show-IFRA
Search-IFRA "vanilla"
Search-IFRA "lemon"
```

---

## 🎨 Metoda 5: Out-GridView (INTERACTIVE WINDOWS)

**Cel mai interactiv mod în PowerShell:**

```powershell
cd "c:\Users\Marcman\Documents\MarcmanMixer\data"
Import-Csv ifra_ingredients_sample.csv | Out-GridView -Title "IFRA Sample - 49 Ingredients"
```

**Caracteristici Out-GridView:**
- ✅ Sortare pe orice coloană (click pe header)
- ✅ Filtrare avansată (Add Criteria)
- ✅ Multi-select
- ✅ Export selecție
- ✅ Căutare instant

---

## 📋 Lista Completă (Text Format)

**Top 20 Bestsellers:**

| # | CAS Number | Nume | Categorie | NCS |
|---|------------|------|-----------|-----|
| 1 | 78-70-6 | **Linalool** | Sintetic | - |
| 2 | 106-24-1 | **Geraniol** | Sintetic | - |
| 3 | 121-33-5 | **Vanillin** | Sintetic | - |
| 4 | 1222-05-5 | **Galaxolide** | Sintetic | - |
| 5 | 5989-27-5 | **d-Limonene** | Sintetic | - |
| 6 | 140-11-4 | **Benzyl acetate** | Sintetic | - |
| 7 | 5392-40-5 | **Citral** | Sintetic | - |
| 8 | 8008-57-9 | **Orange oil sweet** | Natural | **G2.20** |
| 9 | 8006-90-4 | **Peppermint oil** | Natural | **J2.17** |
| 10 | 8000-25-7 | **Rosemary oil** | Natural | **J2.15** |
| 11 | 8016-37-3 | **Myrrh oil** | Natural | **K2.12** |
| 12 | 8000-34-8 | **Clove oil** | Natural | **J2.20** |
| 13 | 120-57-0 | **Piperonal** | Sintetic | - |
| 14 | 13171-00-1 | **Dihydromyrcenol** | Sintetic | - |
| 15 | 106-22-9 | **Citronellol** | Sintetic | - |
| 16 | 115-95-7 | **Linalyl acetate** | Sintetic | - |
| 17 | 8008-31-9 | **Mandarin oil** | Natural | **G2.5** |
| 18 | 68917-33-9 | **Lemon oil terpenes** | Natural | **G2.30** |
| 19 | 8007-00-9 | **Balsam oil Peru** | Natural | **K2.9** |
| 20 | 104-54-1 | **Cinnamyl alcohol** | Sintetic | - |

---

## 📊 Breakdown Categorii

**Total: 49 ingrediente**

| Categorie | Count | % |
|-----------|-------|---|
| Sintetic | 36 | 73% |
| Natural Extracts | 9 | 18% |
| Natural Oils | 2 | 4% |
| Essential Oils | 1 | 2% |

**Categorii IFRA NCS (Naturals):**
- **G2.x** (Citrus): 3 ingrediente (G2.5, G2.20, G2.30)
- **J2.x** (Leaf oils): 3 ingrediente (J2.15, J2.17, J2.20)
- **K2.x** (Balsam): 2 ingrediente (K2.9, K2.12)
- **H2.x** (Seed oils): 1 ingredient (H2.50)
- **F2.x** (Vegetable): 1 ingredient (F2.12)

---

## 🎯 Recomandări

**Pentru VIZUALIZARE:**
👉 **HTML Browser** - `view_ingredients.html` (cel mai frumos!)

**Pentru CĂUTARE:**
👉 **Out-GridView** - `Import-Csv ... | Out-GridView`

**Pentru EDITARE:**
👉 **Excel** - `Start-Process excel ifra_ingredients_sample.csv`

**Pentru PROGRAMARE:**
👉 **CSV în cod Java** - Folosește `IfraDataImporter`

---

## 🚀 Next Steps

După ce ai vizualizat lista:

1. **Importă în database:**
   ```bash
   mvn exec:java -Dexec.mainClass="ro.marcman.mixer.sqlite.IfraDataImporter" ^
                 -Dexec.args="data/ifra_ingredients_sample.csv" ^
                 -pl sqlite
   ```

2. **Asociază cu Arduino** (manual în viitor UI)

3. **Creează rețete** folosind ingredientele

---

**Fișiere disponibile:**
- ✅ `ifra_ingredients_sample.csv` - Date brute
- ✅ `view_ingredients.html` - Vizualizare browser interactivă
- ✅ `IFRA_IMPORT_GUIDE.md` - Ghid complet import
- ✅ `README.md` - Info despre sample data

**Enjoy!** 🌸✨



