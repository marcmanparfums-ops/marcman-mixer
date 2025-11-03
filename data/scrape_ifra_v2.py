#!/usr/bin/env python3
"""
IFRA Transparency List Web Scraper V2
Improved parser for IFRA website structure
"""

import requests
from bs4 import BeautifulSoup
import csv
import time
import sys

BASE_URL = "https://acc.ifrafragrance.org/transparency-list"
OUTPUT_FILE = "ifra_ingredients_full.csv"
TOTAL_PAGES = 151
DELAY = 0.5

def scrape_page(page_num):
    """Scrape one page"""
    url = f"{BASE_URL}?page={page_num}" if page_num > 1 else BASE_URL
    
    print(f"Page {page_num:3d}/{TOTAL_PAGES}...", end=" ", flush=True)
    
    try:
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        r = requests.get(url, headers=headers, timeout=30)
        r.raise_for_status()
        
        soup = BeautifulSoup(r.content, 'html.parser')
        table = soup.find('table')
        
        if not table:
            print("No table")
            return []
        
        ingredients = []
        rows = table.find_all('tr')[1:]  # Skip header
        
        for row in rows:
            cols = row.find_all('td')
            if len(cols) >= 2:
                cas = cols[0].get_text(strip=True)
                name = cols[1].get_text(strip=True)
                ncs = cols[2].get_text(strip=True) if len(cols) > 2 else ""
                
                if cas and name:  # Only valid rows
                    ingredients.append({
                        'cas_number': cas,
                        'name': name,
                        'ifra_naturals_category': ncs
                    })
        
        print(f"{len(ingredients)} items")
        return ingredients
        
    except Exception as e:
        print(f"ERROR: {str(e)[:50]}")
        return []

def categorize(name, ncs):
    """Auto-categorize ingredient"""
    nl = name.lower()
    
    if ncs:
        if ncs.startswith('G'): return "Citrus Oils"
        if ncs.startswith('J'): return "Leaf Oils"
        if ncs.startswith('K'): return "Balsam Oils"
        if ncs.startswith('H'): return "Seed Oils"
        if ncs.startswith('F'): return "Vegetable Oils"
        return "Natural Extracts"
    
    if 'oil' in nl: return "Natural Oils"
    if 'extract' in nl: return "Natural Extracts"
    return "Synthetic"

def describe(name, cat):
    """Generate description"""
    nl = name.lower()
    
    if cat == "Citrus Oils": return "Fresh citrus note"
    if cat == "Leaf Oils": return "Aromatic leaf extract"
    if cat == "Balsam Oils": return "Warm balsamic note"
    if 'vanilla' in nl or 'vanillin' in nl: return "Sweet vanilla note"
    if 'lemon' in nl or 'citral' in nl: return "Fresh lemon citrus note"
    if 'rose' in nl or 'geraniol' in nl: return "Rosy floral note"
    if 'musk' in nl: return "Clean musk note"
    if 'wood' in nl or 'cedar' in nl: return "Woody note"
    if 'floral' in nl or 'flower' in nl: return "Floral note"
    
    return "Fragrance ingredient approved by IFRA"

def main():
    print("=" * 70)
    print("IFRA TRANSPARENCY LIST - WEB SCRAPER V2")
    print("Extracting 3600+ ingredients from all 151 pages")
    print("=" * 70)
    print()
    
    all_data = []
    
    # Scrape all pages
    for page in range(1, TOTAL_PAGES + 1):
        items = scrape_page(page)
        all_data.extend(items)
        
        if page % 10 == 0:
            print(f"  >> Progress: {len(all_data)} ingredients collected so far")
        
        time.sleep(DELAY)
    
    print()
    print("=" * 70)
    print(f"SCRAPING COMPLETE! Total: {len(all_data)} ingredients")
    print("=" * 70)
    print()
    
    # Process data
    print("Processing data...")
    for item in all_data:
        item['category'] = categorize(item['name'], item['ifra_naturals_category'])
        item['description'] = describe(item['name'], item['category'])
    
    # Save CSV
    print(f"Saving to {OUTPUT_FILE}...")
    
    with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['cas_number', 'name', 'ifra_naturals_category', 'category', 'description'])
        writer.writeheader()
        writer.writerows(all_data)
    
    # Statistics
    print()
    print("=" * 70)
    print("SUCCESS! Data saved to:", OUTPUT_FILE)
    print("=" * 70)
    print()
    print(f"Total ingredients: {len(all_data)}")
    
    cats = {}
    for item in all_data:
        c = item['category']
        cats[c] = cats.get(c, 0) + 1
    
    print("\nCategory breakdown:")
    for cat, count in sorted(cats.items(), key=lambda x: x[1], reverse=True):
        print(f"  {cat}: {count}")
    
    ncs_count = sum(1 for item in all_data if item['ifra_naturals_category'])
    print(f"\nWith IFRA NCS category: {ncs_count}")
    
    print("\n" + "=" * 70)
    print("Next: Import to database with import_ifra_full.bat")
    print("=" * 70)

if __name__ == "__main__":
    main()



