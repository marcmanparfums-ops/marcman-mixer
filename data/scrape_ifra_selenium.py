#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IFRA Transparency List - Selenium Scraper
For JavaScript-rendered content
"""

try:
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.webdriver.chrome.options import Options
    import csv
    import time
    
    def scrape_with_selenium():
        """Scrape IFRA list using Selenium for JavaScript rendering"""
        
        print("Initializing Chrome WebDriver...")
        
        chrome_options = Options()
        chrome_options.add_argument('--headless')  # Run in background
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        
        driver = webdriver.Chrome(options=chrome_options)
        
        try:
            url = "https://acc.ifrafragrance.org/transparency-list"
            print(f"Fetching: {url}")
            driver.get(url)
            
            # Wait for table to load
            print("Waiting for content to load...")
            wait = WebDriverWait(driver, 20)
            wait.until(EC.presence_of_element_located((By.TAG_NAME, "table")))
            
            # Scroll to load all content (if lazy loading)
            last_height = driver.execute_script("return document.body.scrollHeight")
            while True:
                driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                time.sleep(2)
                new_height = driver.execute_script("return document.body.scrollHeight")
                if new_height == last_height:
                    break
                last_height = new_height
            
            # Extract all rows
            rows = driver.find_elements(By.CSS_SELECTOR, "table tbody tr")
            print(f"Found {len(rows)} rows")
            
            ingredients = []
            for row in rows:
                cells = row.find_elements(By.TAG_NAME, "td")
                if len(cells) >= 2:
                    name = cells[0].text.strip()
                    cas = cells[1].text.strip()
                    
                    if name and name.lower() != 'ingredient':
                        ingredients.append({
                            'name': name,
                            'cas_number': cas,
                            'category': 'Synthetic',
                            'ifra_ncs': '',
                            'description': ''
                        })
            
            print(f"Extracted {len(ingredients)} ingredients")
            
            # Save to CSV
            with open('ifra_selenium_ingredients.csv', 'w', newline='', encoding='utf-8') as f:
                writer = csv.DictWriter(f, fieldnames=['name', 'cas_number', 'category', 'ifra_ncs', 'description'])
                writer.writeheader()
                writer.writerows(ingredients)
            
            print(f"Saved to ifra_selenium_ingredients.csv")
            
        finally:
            driver.quit()
    
    if __name__ == '__main__':
        scrape_with_selenium()

except ImportError:
    print("ERROR: Selenium not installed!")
    print("Install with: pip install selenium")
    print("\nAlternative: Download ChromeDriver from https://chromedriver.chromium.org/")


