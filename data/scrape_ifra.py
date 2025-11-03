#!/usr/bin/env python3
"""
IFRA Transparency List Web Scraper
Extracts all 3600+ ingredients from IFRA website (151 pages)
Source: https://acc.ifrafragrance.org/transparency-list
"""

import requests
from bs4 import BeautifulSoup
import csv
import time
import sys
from urllib.parse import urljoin

# Configuration
BASE_URL = "https://acc.ifrafragrance.org/transparency-list"
OUTPUT_FILE = "ifra_ingredients_full.csv"
TOTAL_PAGES = 151  # IFRA has 151 pages
DELAY_SECONDS = 0.5  # Delay between requests to be polite

def scrape_page(page_number):
    """Scrape a single page of IFRA ingredients"""
    url = f"{BASE_URL}?page={page_number}" if page_number > 1 else BASE_URL
    
    print(f"Scraping page {page_number}/{TOTAL_PAGES}...", end=" ", flush=True)
    
    try:
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
        response = requests.get(url, headers=headers, timeout=30)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Find the ingredients table
        table = soup.find('table')
        if not table:
            print("❌ No table found")
            return []
        
        ingredients = []
        rows = table.find_all('tr')[1:]  # Skip header row
        
        for row in rows:
            cols = row.find_all('td')
            if len(cols) >= 2:
                cas_number = cols[0].get_text(strip=True)
                principal_name = cols[1].get_text(strip=True)
                naturals_category = cols[2].get_text(strip=True) if len(cols) > 2 else ""
                
                ingredients.append({
                    'cas_number': cas_number,
                    'name': principal_name,
                    'ifra_naturals_category': naturals_category
                })
        
        print(f"OK - {len(ingredients)} ingredients")
        return ingredients
        
    except Exception as e:
        print(f"ERROR: {e}")
        return []

def categorize_ingredient(name, ncs_category):
    """Categorize ingredient based on name and NCS category"""
    name_lower = name.lower()
    
    # Natural categories based on NCS
    if ncs_category:
        if ncs_category.startswith('G'):
            return "Citrus Oils"
        elif ncs_category.startswith('J'):
            return "Leaf Oils"
        elif ncs_category.startswith('K'):
            return "Balsam Oils"
        elif ncs_category.startswith('H'):
            return "Seed Oils"
        elif ncs_category.startswith('F'):
            return "Vegetable Oils"
        else:
            return "Natural Extracts"
    
    # Categorize by name
    if 'oil' in name_lower:
        return "Natural Oils"
    elif 'extract' in name_lower or 'absolute' in name_lower:
        return "Natural Extracts"
    elif 'essence' in name_lower or 'essential' in name_lower:
        return "Essential Oils"
    else:
        return "Synthetic"

def add_descriptions(ingredients):
    """Add generic descriptions based on ingredient type"""
    for ing in ingredients:
        name = ing['name'].lower()
        category = ing.get('category', 'Synthetic')
        
        # Generic descriptions based on category
        if category == "Citrus Oils":
            ing['description'] = "Fresh citrus note used in perfumery"
        elif category == "Leaf Oils":
            ing['description'] = "Aromatic leaf extract with herbaceous character"
        elif category == "Balsam Oils":
            ing['description'] = "Warm balsamic resinous note"
        elif 'oil' in name:
            ing['description'] = "Natural oil component for fragrance formulation"
        elif category == "Synthetic":
            ing['description'] = "Synthetic aroma chemical for perfumery"
        else:
            ing['description'] = "Fragrance ingredient approved by IFRA"
    
    return ingredients

def main():
    print("=" * 60)
    print("IFRA Transparency List - Web Scraper")
    print("Extracting all 3600+ ingredients from IFRA website")
    print("=" * 60)
    print()
    
    all_ingredients = []
    
    # Scrape all pages
    for page in range(1, TOTAL_PAGES + 1):
        ingredients = scrape_page(page)
        all_ingredients.extend(ingredients)
        
        # Progress update every 10 pages
        if page % 10 == 0:
            print(f"Progress: {page}/{TOTAL_PAGES} pages ({len(all_ingredients)} ingredients so far)")
        
        # Be polite - add delay between requests
        time.sleep(DELAY_SECONDS)
    
    print()
    print("=" * 60)
    print(f"SUCCESS! Scraping complete! Total ingredients: {len(all_ingredients)}")
    print("=" * 60)
    print()
    
    # Categorize ingredients
    print("Categorizing ingredients...")
    for ing in all_ingredients:
        ing['category'] = categorize_ingredient(ing['name'], ing['ifra_naturals_category'])
    
    # Add descriptions
    print("Adding descriptions...")
    all_ingredients = add_descriptions(all_ingredients)
    
    # Save to CSV
    print(f"Saving to {OUTPUT_FILE}...")
    
    with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8') as csvfile:
        fieldnames = ['cas_number', 'name', 'ifra_naturals_category', 'category', 'description']
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        
        writer.writeheader()
        writer.writerows(all_ingredients)
    
    print()
    print("=" * 60)
    print("SUCCESS! IFRA data saved to:", OUTPUT_FILE)
    print("=" * 60)
    print()
    print("Statistics:")
    print(f"  Total ingredients: {len(all_ingredients)}")
    
    # Category breakdown
    categories = {}
    for ing in all_ingredients:
        cat = ing.get('category', 'Unknown')
        categories[cat] = categories.get(cat, 0) + 1
    
    print()
    print("  Breakdown by category:")
    for cat, count in sorted(categories.items(), key=lambda x: x[1], reverse=True):
        print(f"    {cat}: {count}")
    
    # NCS categories
    ncs_count = sum(1 for ing in all_ingredients if ing.get('ifra_naturals_category'))
    print()
    print(f"  With IFRA NCS category: {ncs_count}")
    
    print()
    print("=" * 60)
    print("Next steps:")
    print("  1. Review the CSV file")
    print("  2. Import to database:")
    print("     mvn exec:java -Dexec.mainClass='ro.marcman.mixer.sqlite.IfraDataImporter' \\")
    print("                   -Dexec.args='data/ifra_ingredients_full.csv' \\")
    print("                   -pl sqlite")
    print("=" * 60)

if __name__ == "__main__":
    main()

