#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IFRA Transparency List - Advanced Scraper with Multiple Strategies
Attempts to extract ALL 3600+ ingredients using various methods
"""

import requests
from bs4 import BeautifulSoup
import csv
import time
import json
import re

def scrape_ifra_api():
    """Try to find and use IFRA's API if available"""
    print("\n=== Strategy 1: API Detection ===")
    
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Accept': 'application/json, text/plain, */*',
        'X-Requested-With': 'XMLHttpRequest'
    })
    
    # Try common API endpoints
    api_urls = [
        'https://acc.ifrafragrance.org/api/transparency-list',
        'https://acc.ifrafragrance.org/api/ingredients',
        'https://acc.ifrafragrance.org/transparency-list/json',
        'https://acc.ifrafragrance.org/transparency-list/data',
        'https://acc.ifrafragrance.org/views/ajax',
    ]
    
    for url in api_urls:
        try:
            print(f"Trying: {url}")
            response = session.get(url, timeout=10)
            if response.status_code == 200:
                try:
                    data = response.json()
                    print(f"SUCCESS! Found JSON endpoint: {url}")
                    print(f"Response keys: {data.keys() if isinstance(data, dict) else 'list'}")
                    return data
                except:
                    pass
        except Exception as e:
            pass
    
    print("No API endpoint found.")
    return None

def scrape_ifra_paginated():
    """Scrape with intelligent pagination detection"""
    print("\n=== Strategy 2: Smart Pagination ===")
    
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    })
    
    base_url = "https://acc.ifrafragrance.org/transparency-list"
    ingredients = []
    
    # Try different pagination patterns
    pagination_patterns = [
        "?page={}",
        "?p={}",
        "/page/{}",
        "?start={}",
        "?offset={}",
    ]
    
    for pattern in pagination_patterns:
        print(f"\nTrying pagination pattern: {pattern}")
        temp_ingredients = []
        
        for page in range(0, 200):  # Try up to 200 pages
            url = base_url + pattern.format(page)
            
            try:
                response = session.get(url, timeout=15)
                if response.status_code != 200:
                    break
                
                soup = BeautifulSoup(response.content, 'html.parser')
                rows = soup.select('table tbody tr, tr.views-row')
                
                if not rows:
                    break
                
                page_count = 0
                for row in rows:
                    cells = row.find_all(['td', 'th'])
                    if len(cells) >= 2:
                        name = cells[0].get_text().strip()
                        cas = cells[1].get_text().strip()
                        
                        if name and cas and name.lower() not in ['ingredient', 'name']:
                            temp_ingredients.append({'name': name, 'cas': cas})
                            page_count += 1
                
                print(f"  Page {page}: {page_count} ingredients (total: {len(temp_ingredients)})")
                
                if page_count == 0:
                    break
                
                time.sleep(1)
                
            except Exception as e:
                print(f"  Error on page {page}: {e}")
                break
        
        if len(temp_ingredients) > len(ingredients):
            ingredients = temp_ingredients
            print(f"Best pattern so far: {pattern} with {len(ingredients)} ingredients")
    
    return ingredients

def scrape_ifra_lazy_load():
    """Try to detect and handle lazy loading"""
    print("\n=== Strategy 3: Lazy Load Detection ===")
    
    print("This strategy requires Selenium (browser automation)")
    print("Run: pip install selenium")
    print("And download ChromeDriver")
    
    return []

def scrape_drupal_views():
    """Try Drupal Views JSON endpoint (IFRA site uses Drupal)"""
    print("\n=== Strategy 4: Drupal Views Exploitation ===")
    
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0',
        'Accept': 'application/json'
    })
    
    # Drupal Views often expose REST endpoints
    drupal_patterns = [
        'https://acc.ifrafragrance.org/transparency-list?_format=json',
        'https://acc.ifrafragrance.org/api/transparency-list/rest',
        'https://acc.ifrafragrance.org/rest/transparency-list',
        'https://acc.ifrafragrance.org/jsonapi/node/transparency_list',
    ]
    
    for url in drupal_patterns:
        try:
            print(f"Trying: {url}")
            response = session.get(url, timeout=10)
            if response.status_code == 200 and 'json' in response.headers.get('Content-Type', ''):
                data = response.json()
                print(f"SUCCESS! Found Drupal endpoint")
                return data
        except:
            pass
    
    print("No Drupal REST endpoint found")
    return None

def main():
    print("=" * 70)
    print("IFRA COMPLETE SCRAPER - ADVANCED STRATEGIES")
    print("=" * 70)
    
    all_ingredients = []
    
    # Try all strategies
    strategies = [
        scrape_ifra_api,
        scrape_ifra_paginated,
        scrape_drupal_views,
    ]
    
    for strategy in strategies:
        try:
            result = strategy()
            if result:
                if isinstance(result, list):
                    all_ingredients.extend(result)
                print(f"\nTotal ingredients so far: {len(all_ingredients)}")
        except Exception as e:
            print(f"Strategy failed: {e}")
    
    # Remove duplicates
    unique_ingredients = []
    seen_cas = set()
    
    for ing in all_ingredients:
        cas = ing.get('cas', ing.get('casNumber', ''))
        if cas and cas not in seen_cas:
            seen_cas.add(cas)
            unique_ingredients.append(ing)
    
    print(f"\n\nFinal count: {len(unique_ingredients)} unique ingredients")
    
    if unique_ingredients:
        # Save to CSV
        with open('ifra_all_scraped.csv', 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=['name', 'cas_number', 'category', 'ifra_ncs', 'description'])
            writer.writeheader()
            for ing in unique_ingredients:
                writer.writerow({
                    'name': ing.get('name', ''),
                    'cas_number': ing.get('cas', ing.get('casNumber', '')),
                    'category': ing.get('category', 'Synthetic'),
                    'ifra_ncs': ing.get('ifra_ncs', ''),
                    'description': ing.get('description', '')
                })
        
        print(f"Saved to: ifra_all_scraped.csv")
    else:
        print("\nNo ingredients found. Manual list creation recommended.")

if __name__ == '__main__':
    main()


