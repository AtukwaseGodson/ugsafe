import os
from icrawler.builtin import BingImageCrawler

save_directory = 'incident_dataset/uganda_accidents'
os.makedirs(save_directory, exist_ok=True)

# List of specific phrases that will all lead to the images you want
queries = [
    'road accidents in Uganda aftermath',
    'Uganda police accident scene photos',
    'major car crash wreckage Uganda',
    'Uganda highway accident scene ground level',
    'overturned taxi Uganda accident',
    'Uganda traffic accident severe damage',
    'boda boda accident aftermath Uganda',
    'Kampala Masaka road accident images',
    'bus accident Uganda wreckage',
    'towing crashed cars Uganda'
    # --- Location Based (Black Spots) ---
    'Kampala-Jinja highway accident wreckage',
    'Kampala-Masaka road accident aftermath',
    'Mabira forest accident scene photos',
    'Luckia junction accident Uganda',
    'Bwaise roundabout accident wreckage',
    
    # --- Local Vehicle Types ---
    'boda boda accident wreckage Uganda',
    'matatu taxi accident Uganda aftermath',
    'Uganda bus accident Gulu highway',
    'Uganda sugar cane truck accident scene',
    'overturned fuel tanker Uganda road',
    
    # --- Incident Descriptions ---
    'severe head-on collision Uganda',
    'multiple vehicle pile up Uganda highway',
    'Uganda police traffic accident report photos',
    'broken down truck road hazard Uganda',
    'fatal road crash scene Uganda'
]

for query in queries:
    print(f"--- Scraping for: {query} ---")
    # We set a smaller limit per query to stay 'under the radar'
    crawler = BingImageCrawler(storage={'root_dir': save_directory})
    crawler.crawl(keyword=query, max_num=100) 

print(f"Done! Check your folder now. You should have hundreds of images.")