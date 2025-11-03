#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IFRA Transparency List - Complete Scraper
Extracts ALL 3000+ ingredients from IFRA website
"""

import requests
from bs4 import BeautifulSoup
import csv
import time
import re
from urllib.parse import urljoin

BASE_URL = "https://acc.ifrafragrance.org"
LIST_URL = f"{BASE_URL}/transparency-list"

def clean_text(text):
    """Clean text from HTML entities and extra whitespace"""
    if not text:
        return ""
    text = text.strip()
    text = re.sub(r'\s+', ' ', text)
    return text

def scrape_all_ingredients():
    """Scrape all IFRA ingredients with pagination"""
    
    print("Starting IFRA Complete Scraper...")
    print(f"Fetching: {LIST_URL}")
    
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    })
    
    ingredients = []
    page = 1
    max_pages = 200  # Safety limit
    
    while page <= max_pages:
        try:
            url = f"{LIST_URL}?page={page}" if page > 1 else LIST_URL
            print(f"\nFetching page {page}: {url}")
            
            response = session.get(url, timeout=30)
            response.raise_for_status()
            
            soup = BeautifulSoup(response.content, 'html.parser')
            
            # Find ingredient table rows
            rows = soup.select('table.views-table tbody tr, table tbody tr, tr.views-row')
            
            if not rows:
                print(f"No rows found on page {page}. Trying alternative selectors...")
                # Try alternative table structure
                rows = soup.find_all('tr', class_=re.compile(r'(odd|even|views-row)'))
            
            if not rows:
                print(f"Still no rows found. Checking for pagination...")
                # Check if there's a next page
                next_link = soup.select_one('a[rel="next"], .pager-next a, .pagination .next a')
                if not next_link:
                    print(f"No more pages found. Total pages scraped: {page - 1}")
                    break
                page += 1
                time.sleep(1)
                continue
            
            print(f"Found {len(rows)} rows on page {page}")
            page_ingredients = 0
            
            for row in rows:
                cells = row.find_all(['td', 'th'])
                
                # Skip header rows
                if not cells or row.find('th'):
                    continue
                
                # Expected columns: Ingredient Name, CAS Number, IFRA Category, Description
                # Or variations thereof
                if len(cells) >= 2:
                    ingredient_name = clean_text(cells[0].get_text())
                    cas_number = clean_text(cells[1].get_text()) if len(cells) > 1 else ""
                    
                    # Skip if name is empty or looks like a header
                    if not ingredient_name or ingredient_name.lower() in ['ingredient', 'name', 'cas', 'category']:
                        continue
                    
                    # Extract IFRA NCS category if present
                    ifra_ncs = ""
                    if len(cells) > 2:
                        third_col = clean_text(cells[2].get_text())
                        # Check if it matches IFRA NCS pattern (e.g., "A2.15", "G2.20")
                        if re.match(r'^[A-Z]\d+\.\d+', third_col):
                            ifra_ncs = third_col
                    
                    # Category and description
                    category = "Synthetic"
                    description = ""
                    
                    if ifra_ncs:
                        category = "Natural"
                        description = f"IFRA Natural Complex Substance {ifra_ncs}"
                    
                    # Try to extract description from remaining cells
                    if len(cells) > 3:
                        desc_text = clean_text(cells[3].get_text())
                        if desc_text:
                            description = desc_text
                    
                    # Add ingredient
                    ingredient = {
                        'name': ingredient_name,
                        'cas_number': cas_number,
                        'category': category,
                        'ifra_ncs': ifra_ncs,
                        'description': description
                    }
                    
                    # Check for duplicates
                    if cas_number and not any(i['cas_number'] == cas_number for i in ingredients):
                        ingredients.append(ingredient)
                        page_ingredients += 1
            
            print(f"Extracted {page_ingredients} ingredients from page {page}")
            print(f"Total so far: {len(ingredients)}")
            
            # Check for next page
            next_link = soup.select_one('a[rel="next"], .pager-next a, .pagination .next a, li.next a')
            if not next_link:
                print(f"No next page link found. Scraping complete!")
                break
            
            page += 1
            time.sleep(2)  # Be polite to the server
            
        except requests.RequestException as e:
            print(f"Error fetching page {page}: {e}")
            break
        except Exception as e:
            print(f"Error parsing page {page}: {e}")
            break
    
    return ingredients

def save_to_csv(ingredients, filename='ifra_all_ingredients.csv'):
    """Save ingredients to CSV file"""
    
    print(f"\nSaving {len(ingredients)} ingredients to {filename}...")
    
    with open(filename, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['name', 'cas_number', 'category', 'ifra_ncs', 'description'])
        writer.writeheader()
        writer.writerows(ingredients)
    
    print(f"SUCCESS! Saved to {filename}")
    print(f"\nStatistics:")
    print(f"  Total ingredients: {len(ingredients)}")
    
    natural_count = sum(1 for i in ingredients if i['category'] == 'Natural')
    synthetic_count = len(ingredients) - natural_count
    
    print(f"  Natural (NCS): {natural_count}")
    print(f"  Synthetic: {synthetic_count}")
    print(f"  With CAS: {sum(1 for i in ingredients if i['cas_number'])}")
    print(f"  With IFRA NCS: {sum(1 for i in ingredients if i['ifra_ncs'])}")

if __name__ == '__main__':
    print("=" * 70)
    print("IFRA COMPLETE SCRAPER - ALL 3000+ INGREDIENTS")
    print("=" * 70)
    
    ingredients = scrape_all_ingredients()
    
    if ingredients:
        save_to_csv(ingredients, 'ifra_all_ingredients.csv')
        print("\nDone!")
    else:
        print("\nNo ingredients found. The website structure may have changed.")
        print("Manual download may be required.")


