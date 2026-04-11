/**
 * Product-only corpus for Fuse.js fuzzy autocomplete.
 * Covers hardware, gadgets, vehicles, supplements, and physical goods.
 * Deliberately excludes services, travel, apps, and experiences.
 *
 * To extend: add product names as plain strings.
 * Fuse.js will fuzzy-match against this list client-side with zero latency.
 */
export const SEARCH_SUGGESTIONS: string[] = [
  // ── Smartphones ───────────────────────────────────────────────────────────
  'iPhone 16 Pro Max', 'iPhone 16 Pro', 'iPhone 16', 'iPhone 15 Pro',
  'iPhone 15', 'iPhone 14', 'iPhone 13',
  'Samsung Galaxy S24 Ultra', 'Samsung Galaxy S24', 'Samsung Galaxy S23',
  'Samsung Galaxy A55', 'Samsung Galaxy Z Fold 6', 'Samsung Galaxy Z Flip 6',
  'Google Pixel 9 Pro', 'Google Pixel 9', 'Google Pixel 8 Pro', 'Google Pixel 8',
  'OnePlus 12', 'OnePlus Nord 4', 'Nothing Phone 2a', 'Nothing Phone 2',
  'Xiaomi 14 Ultra', 'Xiaomi 14', 'Redmi Note 13 Pro', 'Realme GT 6',
  'Motorola Edge 50 Pro', 'iQOO 12',

  // ── Laptops ───────────────────────────────────────────────────────────────
  'MacBook Pro M4', 'MacBook Pro M3', 'MacBook Air M3', 'MacBook Air M2',
  'Dell XPS 15', 'Dell XPS 13', 'Dell Inspiron 15',
  'Lenovo ThinkPad X1 Carbon', 'Lenovo IdeaPad Slim 5',
  'ASUS ZenBook 14', 'ASUS ROG Zephyrus G14', 'ASUS TUF Gaming A15',
  'HP Spectre x360', 'HP Pavilion 15', 'HP OMEN 16',
  'Microsoft Surface Laptop 5', 'Acer Swift X', 'Razer Blade 16',
  'LG Gram 16', 'MSI Stealth 16',

  // ── Tablets ───────────────────────────────────────────────────────────────
  'iPad Pro M4', 'iPad Air M2', 'iPad mini 7', 'iPad 10th gen',
  'Samsung Galaxy Tab S10', 'OnePlus Pad 2', 'Xiaomi Pad 6',

  // ── Headphones & Earbuds ─────────────────────────────────────────────────
  'Sony WH-1000XM5', 'Sony WH-1000XM4', 'Sony WF-1000XM5',
  'Bose QuietComfort 45', 'Bose QuietComfort Ultra',
  'Apple AirPods Pro 2nd gen', 'Apple AirPods 4',
  'Samsung Galaxy Buds 3 Pro', 'Samsung Galaxy Buds 2 Pro',
  'Jabra Evolve2 85', 'Sennheiser Momentum 4',
  'Nothing Ear 2', 'OnePlus Buds 3', 'Boat Airdopes 141',

  // ── Monitors ──────────────────────────────────────────────────────────────
  'LG UltraWide 34 inch', 'Dell UltraSharp U2723DE',
  'Samsung Odyssey G7', 'ASUS ProArt PA278QV',
  'BenQ PD3220U', 'Apple Studio Display',

  // ── Cameras ───────────────────────────────────────────────────────────────
  'Sony A7 IV', 'Sony A7C II', 'Sony A6700', 'Sony ZV-E10',
  'Canon EOS R8', 'Canon EOS R50', 'Canon EOS R6 Mark II',
  'Nikon Z6 III', 'Nikon Z30', 'Fujifilm X-T5', 'Fujifilm X100VI',
  'GoPro Hero 13', 'DJI Osmo Pocket 3', 'DJI Mini 4 Pro',

  // ── Smartwatches & Wearables ─────────────────────────────────────────────
  'Apple Watch Series 10', 'Apple Watch Ultra 2',
  'Samsung Galaxy Watch 7', 'Samsung Galaxy Watch Ultra',
  'Garmin Fenix 7', 'Garmin Venu 3', 'Garmin Forerunner 255',
  'Fitbit Charge 6', 'Amazfit GTR 4', 'Amazfit Balance',
  'OnePlus Watch 2', 'Noise Colorfit Pro 4',

  // ── Motorcycles ───────────────────────────────────────────────────────────
  'Triumph Scrambler 400 X', 'Triumph Speed 400', 'Triumph Tiger Sport 660',
  'Royal Enfield Himalayan 450', 'Royal Enfield Guerrilla 450',
  'Royal Enfield Classic 350', 'Royal Enfield Bullet 350',
  'Royal Enfield Super Meteor 650', 'Royal Enfield Continental GT 650',
  'KTM Duke 390', 'KTM Adventure 390', 'KTM RC 390',
  'Honda CB350', 'Honda Hornet 2.0', 'Honda CB750 Hornet',
  'Bajaj Pulsar NS200', 'Bajaj Dominar 400',
  'Hero Xpulse 200 4V', 'TVS Apache RTR 310', 'Yamaha MT-15',

  // ── Cars ──────────────────────────────────────────────────────────────────
  'Tata Nexon EV', 'Tata Punch EV', 'Tata Curvv',
  'Hyundai Creta', 'Hyundai Alcazar', 'Hyundai Ioniq 5',
  'Maruti Suzuki Brezza', 'Maruti Suzuki Jimny',
  'Honda Elevate', 'Honda City',
  'Mahindra XUV700', 'Mahindra Thar Roxx', 'Mahindra BE 6',
  'Kia Seltos', 'Kia Carens', 'Toyota Fortuner', 'Toyota Innova HyCross',
  'MG Hector', 'MG Windsor EV', 'Skoda Slavia', 'Volkswagen Taigun',

  // ── Grooming & Personal Care ─────────────────────────────────────────────
  'Philips Norelco OneBlade 9000', 'Braun Series 9 Pro',
  'Dyson Airwrap', 'Dyson Supersonic', 'GHD Platinum+',
  'Gillette Fusion ProGlide', 'Bombay Shaving Company kit',
  'CeraVe moisturiser', 'The Ordinary Niacinamide', 'Minimalist Vitamin C',

  // ── Supplements & Nutrition ──────────────────────────────────────────────
  'Creatine monohydrate', 'Whey protein isolate',
  'Optimum Nutrition Gold Standard Whey', 'MyProtein Impact Whey',
  'MuscleBlaze Whey Active', 'Ashwagandha KSM-66',
  'Magnesium glycinate', 'Vitamin D3 K2', 'Omega 3 fish oil',
  'Collagen peptides', 'Pre-workout supplement',

  // ── Gaming ────────────────────────────────────────────────────────────────
  'PlayStation 5 Slim', 'Xbox Series X', 'Nintendo Switch OLED',
  'Steam Deck OLED', 'Razer DeathAdder V3',
  'Logitech G Pro X Superlight', 'HyperX Cloud Alpha',
  'Razer Huntsman V3 Pro keyboard',
];
