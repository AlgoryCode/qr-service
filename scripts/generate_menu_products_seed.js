/**
 * Generates menu-products.json + menu-products.sql from catalog of items.
 * Run: node scripts/generate_menu_products_seed.js
 */
const fs = require("fs");
const path = require("path");

const MENU_ID = 4;
const root = path.join(__dirname, "..");
const taxonomy = JSON.parse(
  fs.readFileSync(path.join(root, "src/main/resources/seed/menu-taxonomy.json"), "utf8")
);

const nutrition = (basis, kcal, extras = {}) => {
  const energyKcal = kcal;
  const energyKj = Math.round(kcal * 4.184);
  return {
    basis,
    energyKj,
    energyKcal,
    fat: extras.fat ?? 5,
    saturatedFat: extras.saturatedFat ?? 1.5,
    carbohydrate: extras.carbohydrate ?? 10,
    sugars: extras.sugars ?? 3,
    fibre: extras.fibre ?? 1,
    protein: extras.protein ?? 5,
    salt: extras.salt ?? 0.5,
  };
};

/** @type {Record<string, Array<{name:string,description:string,price:number,tags:string[],basis?:string,kcal?:number,n?:object}>>} */
const catalog = {
  sicak_icecekler: [
    { name: "Türk Kahvesi", description: "Közde pişmiş klasik Türk kahvesi", price: 90, tags: ["seker_ilavesiz", "populer"], basis: "PER_100ML", kcal: 2, n: { fat: 0.1, carbohydrate: 0.3, protein: 0.1, salt: 0 } },
    { name: "Filtre Kahve", description: "Taze çekilmiş filtre kahve", price: 110, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 2, n: { fat: 0, carbohydrate: 0.2, protein: 0.1, salt: 0 } },
    { name: "Espresso", description: "Tek shot espresso", price: 85, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 3 },
    { name: "Americano", description: "Espresso ve sıcak su", price: 100, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 2 },
    { name: "Cappuccino", description: "Espresso, buharlı süt, süt köpüğü", price: 130, tags: ["vejeteryan", "populer"], basis: "PER_100ML", kcal: 45, n: { fat: 2.5, carbohydrate: 3.5, protein: 2.5 } },
  ],
  soguk_icecekler: [
    { name: "Limonata", description: "Ev yapımı taze limonata", price: 95, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 29 },
    { name: "Ice Tea Şeftali", description: "Soğuk şeftali çayı", price: 90, tags: ["vegan"], basis: "PER_100ML", kcal: 28 },
    { name: "Cola", description: "Soğuk kola", price: 70, tags: ["vegan"], basis: "PER_100ML", kcal: 42 },
    { name: "Gazoz", description: "Klasik gazoz", price: 65, tags: ["vegan"], basis: "PER_100ML", kcal: 38 },
    { name: "Ayran", description: "Ev yapımı yayık ayran", price: 55, tags: ["vejeteryan", "glutensiz", "populer"], basis: "PER_100ML", kcal: 35, n: { fat: 1.5, protein: 2, carbohydrate: 3 } },
  ],
  taze_sikilmis_meyve_sulari: [
    { name: "Portakal Suyu", description: "Taze sıkılmış portakal", price: 120, tags: ["vegan", "glutensiz", "seker_ilavesiz"], basis: "PER_100ML", kcal: 45 },
    { name: "Elma Suyu", description: "Taze sıkılmış elma", price: 115, tags: ["vegan", "glutensiz", "seker_ilavesiz"], basis: "PER_100ML", kcal: 46 },
    { name: "Havuç Portakal", description: "Havuç ve portakal karışımı", price: 130, tags: ["vegan", "glutensiz", "yeni"], basis: "PER_100ML", kcal: 42 },
  ],
  fermente_icecekler: [
    { name: "Kombucha Zencefil", description: "Ev yapımı zencefilli kombucha", price: 140, tags: ["vegan", "seker_ilavesiz", "yeni"], basis: "PER_100ML", kcal: 18 },
    { name: "Şalgam Suyu", description: "Acılı şalgam", price: 60, tags: ["vegan", "acili", "glutensiz"], basis: "PER_100ML", kcal: 12 },
  ],
  alkollu_icecekler: [
    { name: "Efes Pilsen", description: "33cl şişe bira", price: 150, tags: [], basis: "PER_100ML", kcal: 43 },
    { name: "Kırmızı Şarap Kadeh", description: "Ev şarabı kadeh", price: 220, tags: ["vegan"], basis: "PER_100ML", kcal: 85 },
    { name: "Rakı Tek", description: "Tek duble rakı", price: 280, tags: [], basis: "PER_100ML", kcal: 230 },
  ],
  sutlu_icecekler: [
    { name: "Sıcak Çikolata", description: "Sütlü sıcak çikolata", price: 125, tags: ["vejeteryan"], basis: "PER_100ML", kcal: 89 },
    { name: "Latte", description: "Espresso ve buharlı süt", price: 135, tags: ["vejeteryan", "populer"], basis: "PER_100ML", kcal: 48 },
    { name: "Laktozsuz Latte", description: "Laktozsuz süt ile latte", price: 145, tags: ["laktozsuz", "vejeteryan"], basis: "PER_100ML", kcal: 42 },
  ],
  caylar: [
    { name: "Çay", description: "İnce belli bardak demlik çay", price: 40, tags: ["vegan", "seker_ilavesiz", "populer"], basis: "PER_100ML", kcal: 1 },
    { name: "Adaçayı", description: "Taze adaçayı", price: 70, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 1 },
    { name: "Ihlamur", description: "Ballı ıhlamur", price: 75, tags: ["vejeteryan"], basis: "PER_100ML", kcal: 18 },
  ],
  smoothie_ve_shake: [
    { name: "Çilek Smoothie", description: "Çilek, muz, yoğurt", price: 160, tags: ["vejeteryan", "yeni"], basis: "PER_100ML", kcal: 72 },
    { name: "Protein Shake", description: "Muzlu whey protein shake", price: 180, tags: ["sef_ozel"], basis: "PER_100ML", kcal: 95, n: { protein: 12, carbohydrate: 8, fat: 2 } },
    { name: "Yeşil Detoks", description: "Ispanak, elma, limon smoothie", price: 170, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 55 },
  ],
  et_suyu_corbalar: [
    { name: "Ezogelin Çorbası", description: "Nane ve pul biberli", price: 130, tags: ["acili", "populer"], kcal: 74 },
    { name: "İşkembe Çorbası", description: "Sarımsaklı işkembe", price: 180, tags: ["acili"], kcal: 95 },
    { name: "Tavuk Suyu Çorba", description: "Şehriyeli tavuk suyu", price: 125, tags: [], kcal: 48 },
  ],
  kremali_corbalar: [
    { name: "Mercimek Çorbası", description: "Tereyağlı mercimek", price: 130, tags: ["vejeteryan", "glutensiz", "populer"], kcal: 67 },
    { name: "Mantar Çorbası", description: "Kremalı mantar", price: 145, tags: ["vejeteryan"], kcal: 82 },
    { name: "Brokoli Çorbası", description: "Kremalı brokoli", price: 140, tags: ["vejeteryan", "glutensiz"], kcal: 70 },
  ],
  deniz_urunu_corbalar: [
    { name: "Balık Çorbası", description: "Mevsim balığı ile", price: 190, tags: ["laktozsuz"], kcal: 78 },
    { name: "Karides Çorbası", description: "Krema ve karides", price: 210, tags: ["sef_ozel"], kcal: 92 },
  ],
  sebze_corbalar: [
    { name: "Domates Çorbası", description: "Fesleğenli domates", price: 120, tags: ["vegan", "glutensiz"], kcal: 42 },
    { name: "Tarhana Çorbası", description: "Ev yapımı tarhana", price: 115, tags: ["vejeteryan"], kcal: 55 },
  ],
  soguk_baslangiclar: [
    { name: "Zeytin Tabağı", description: "Karışık zeytin", price: 110, tags: ["vegan", "glutensiz"], kcal: 145 },
    { name: "Peynir Tabağı", description: "Üç çeşit peynir", price: 180, tags: ["vejeteryan", "glutensiz"], kcal: 280 },
    { name: "Antipasto Mini", description: "Salam, zeytin, grissini", price: 200, tags: ["yeni"], kcal: 260 },
  ],
  sicak_baslangiclar: [
    { name: "Paçanga Böreği", description: "Pastırmalı paçanga", price: 160, tags: ["populer"], kcal: 310 },
    { name: "Kalamar Tava", description: "Çıtır kalamar", price: 240, tags: [], kcal: 220 },
    { name: "Mücver", description: "Kabaklı mücver", price: 140, tags: ["vejeteryan"], kcal: 180 },
  ],
  salatalar: [
    { name: "Çoban Salata", description: "Domates, salatalık, soğan", price: 140, tags: ["vegan", "glutensiz", "populer"], kcal: 50 },
    { name: "Sezar Salata", description: "Tavuklu Sezar", price: 220, tags: [], kcal: 160 },
    { name: "Akdeniz Salata", description: "Roka, zeytin, peynir", price: 180, tags: ["vejeteryan", "glutensiz"], kcal: 120 },
    { name: "Quinoa Salata", description: "Quinoa, avokado, nar", price: 210, tags: ["vegan", "glutensiz", "yeni"], kcal: 145 },
  ],
  soguk_mezeler: [
    { name: "Humus", description: "Tahinli nohut humusu", price: 120, tags: ["vegan", "glutensiz", "populer"], kcal: 165 },
    { name: "Haydari", description: "Süzme yoğurtlu haydari", price: 110, tags: ["vejeteryan", "glutensiz"], kcal: 95 },
    { name: "Acılı Ezme", description: "Acılı domates ezmesi", price: 100, tags: ["vegan", "acili", "glutensiz"], kcal: 70 },
    { name: "Atom", description: "Acılı atom meze", price: 115, tags: ["vejeteryan", "acili"], kcal: 110 },
  ],
  sicak_mezeler: [
    { name: "Sigara Böreği", description: "Peynirli çıtır börek", price: 150, tags: ["vejeteryan", "populer"], kcal: 298 },
    { name: "İçli Köfte", description: "2 adet içli köfte", price: 170, tags: [], kcal: 250 },
    { name: "Arnavut Ciğeri", description: "Kızartma ciğer", price: 190, tags: ["acili"], kcal: 210 },
  ],
  zeytinyaglilar: [
    { name: "Zeytinyağlı Yaprak Sarma", description: "8 adet sarma", price: 160, tags: ["vegan", "glutensiz"], kcal: 180 },
    { name: "Zeytinyağlı Enginar", description: "Bakla ve havuç ile", price: 175, tags: ["vegan", "glutensiz"], kcal: 95 },
    { name: "İmam Bayıldı", description: "Zeytinyağlı patlıcan", price: 165, tags: ["vegan", "glutensiz"], kcal: 140 },
  ],
  et_yemekleri: [
    { name: "Izgara Köfte", description: "Dana kıyma köfte, pilav", price: 320, tags: ["glutensiz", "populer"], kcal: 220 },
    { name: "Kuzu Tandır", description: "Fırın kuzu tandır", price: 480, tags: ["sef_ozel", "glutensiz"], kcal: 280 },
    { name: "Dana Rosto", description: "Sebzeli dana rosto", price: 420, tags: ["glutensiz"], kcal: 240 },
    { name: "Hünkar Beğendi", description: "Kuzu ve beğendi", price: 390, tags: ["sef_ozel"], kcal: 260 },
  ],
  tavuk_yemekleri: [
    { name: "Tavuk Şiş", description: "Marine tavuk şiş", price: 280, tags: ["glutensiz", "populer"], kcal: 167 },
    { name: "Tavuk Sote", description: "Sebzeli tavuk sote", price: 260, tags: ["glutensiz"], kcal: 155 },
    { name: "Krema Soslu Tavuk", description: "Mantarlı krema sos", price: 290, tags: [], kcal: 210 },
  ],
  vejeteryan_ana_yemekler: [
    { name: "Sebzeli Güveç", description: "Fırın mevsim sebzeleri", price: 240, tags: ["vejeteryan", "glutensiz"], kcal: 100 },
    { name: "Mantı", description: "Yoğurtlu ev mantısı", price: 250, tags: ["vejeteryan", "populer"], kcal: 220 },
    { name: "Patlıcan Musakka", description: "Fırın musakka", price: 230, tags: ["vejeteryan"], kcal: 165 },
  ],
  vegan_ana_yemekler: [
    { name: "Mercimek Köftesi", description: "Baharatlı mercimek köftesi", price: 180, tags: ["vegan", "acili"], kcal: 186 },
    { name: "Nohut Yemeği", description: "Zeytinyağlı nohut", price: 170, tags: ["vegan", "glutensiz"], kcal: 140 },
    { name: "Falafel Tabağı", description: "Humus ve salata ile", price: 200, tags: ["vegan", "yeni"], kcal: 195 },
  ],
  makarna_cesitleri: [
    { name: "Spaghetti Bolognese", description: "Kıymalı bolognese", price: 260, tags: ["populer"], kcal: 210 },
    { name: "Penne Arrabbiata", description: "Acılı domates sos", price: 230, tags: ["vegan", "acili"], kcal: 175 },
    { name: "Fettuccine Alfredo", description: "Krema ve parmesan", price: 270, tags: ["vejeteryan"], kcal: 280 },
  ],
  pilav_cesitleri: [
    { name: "Tereyağlı Pilav", description: "Pirinç pilavı", price: 90, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
    { name: "Bulgur Pilavı", description: "Domatesli bulgur", price: 95, tags: ["vegan"], kcal: 145 },
    { name: "İç Pilav", description: "Fıstıklı iç pilav", price: 120, tags: ["vejeteryan"], kcal: 175 },
  ],
  guvec_yemekleri: [
    { name: "Kuzu Güveç", description: "Fırın kuzu güveç", price: 410, tags: ["glutensiz", "sef_ozel"], kcal: 250 },
    { name: "Türlü Güveç", description: "Sebze türlü", price: 220, tags: ["vegan", "glutensiz"], kcal: 95 },
    { name: "Mantarlı Güveç", description: "Kaşarlı mantar güveç", price: 240, tags: ["vejeteryan", "glutensiz"], kcal: 130 },
  ],
  kiyma_pideler: [
    { name: "Kıymalı Pide", description: "Klasik kıymalı pide", price: 260, tags: ["acili", "populer"], kcal: 250 },
    { name: "Kuşbaşılı Pide", description: "Kuşbaşı etli pide", price: 310, tags: [], kcal: 270 },
  ],
  peynirli_pideler: [
    { name: "Kaşarlı Pide", description: "Bol kaşarlı pide", price: 240, tags: ["vejeteryan", "populer"], kcal: 265 },
    { name: "Üç Peynirli Pide", description: "Kaşar, lor, beyaz peynir", price: 270, tags: ["vejeteryan"], kcal: 280 },
  ],
  karisik_pideler: [
    { name: "Karışık Pide", description: "Kıyma ve kaşar", price: 290, tags: ["populer"], kcal: 275 },
    { name: "Pastırmalı Pide", description: "Pastırma ve yumurta", price: 320, tags: ["sef_ozel"], kcal: 295 },
  ],
  lahmacun: [
    { name: "Lahmacun", description: "İnce hamur lahmacun", price: 120, tags: ["populer"], kcal: 210 },
    { name: "Fındık Lahmacun", description: "Küçük boy 3 adet", price: 150, tags: [], kcal: 200 },
  ],
  klasik_pizzalar: [
    { name: "Margarita Pizza", description: "Domates, mozzarella, fesleğen", price: 290, tags: ["vejeteryan", "populer"], kcal: 267 },
    { name: "Pepperoni Pizza", description: "Pepperoni ve mozzarella", price: 330, tags: [], kcal: 290 },
    { name: "Karışık Pizza", description: "Sucuk, mantar, biber", price: 340, tags: [], kcal: 285 },
  ],
  ozel_pizzalar: [
    { name: "Trüf Mantarlı Pizza", description: "Trüf yağı ve mantar", price: 420, tags: ["vejeteryan", "sef_ozel"], kcal: 300 },
    { name: "BBQ Tavuk Pizza", description: "Barbekü soslu tavuk", price: 360, tags: ["yeni"], kcal: 275 },
  ],
  ince_kruvasan_pizzalar: [
    { name: "Kruvasan Pizza Klasik", description: "İnce kruvasan hamur", price: 310, tags: ["vejeteryan"], kcal: 255 },
    { name: "Kruvasan Pizza Sucuklu", description: "Sucuk ve kaşar", price: 330, tags: [], kcal: 270 },
  ],
  klasik_sandvicler: [
    { name: "Tavuklu Sandviç", description: "Izgara tavuk sandviç", price: 200, tags: ["populer"], kcal: 240 },
    { name: "Ton Balıklı Sandviç", description: "Ton ve yeşillik", price: 190, tags: [], kcal: 210 },
    { name: "Sebzeli Sandviç", description: "Izgara sebze", price: 170, tags: ["vegan"], kcal: 180 },
  ],
  tostlar: [
    { name: "Kaşarlı Tost", description: "Klasik kaşar tost", price: 120, tags: ["vejeteryan", "populer"], kcal: 280 },
    { name: "Sucuklu Tost", description: "Sucuk ve kaşar", price: 150, tags: [], kcal: 320 },
    { name: "Ayvalık Tostu", description: "Bol malzemeli", price: 180, tags: [], kcal: 350 },
  ],
  wraplar: [
    { name: "Tavuk Wrap", description: "Izgara tavuk wrap", price: 210, tags: ["populer"], kcal: 230 },
    { name: "Falafel Wrap", description: "Falafel ve humus", price: 190, tags: ["vegan"], kcal: 215 },
  ],
  klasik_burgerler: [
    { name: "Cheeseburger", description: "Dana köfte, cheddar", price: 310, tags: ["populer"], kcal: 282 },
    { name: "Classic Burger", description: "Dana köfte, turşu, sos", price: 290, tags: [], kcal: 265 },
    { name: "Double Burger", description: "Çift köfte", price: 380, tags: [], kcal: 340 },
  ],
  ozel_burgerler: [
    { name: "Truffle Burger", description: "Trüf mayonez ve cheddar", price: 420, tags: ["sef_ozel"], kcal: 310 },
    { name: "BBQ Bacon Burger", description: "Barbekü ve pastırma", price: 390, tags: ["yeni"], kcal: 330 },
  ],
  vejeteryan_burgerler: [
    { name: "Mantar Burger", description: "Portobello mantar", price: 270, tags: ["vejeteryan"], kcal: 210 },
    { name: "Falafel Burger", description: "Falafel köfte", price: 250, tags: ["vegan"], kcal: 230 },
  ],
  sutlu_tatlilar: [
    { name: "Sütlaç", description: "Fırın sütlaç", price: 140, tags: ["vejeteryan", "glutensiz", "populer"], kcal: 129 },
    { name: "Kazandibi", description: "Karamelize kazandibi", price: 150, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
    { name: "Muhallebi", description: "Gül suları muhallebi", price: 130, tags: ["vejeteryan", "glutensiz"], kcal: 120 },
  ],
  serbetli_tatlilar: [
    { name: "Baklava", description: "Antep fıstıklı baklava", price: 180, tags: ["vejeteryan", "populer"], kcal: 428 },
    { name: "Künefe", description: "Sıcak künefe", price: 200, tags: ["vejeteryan"], kcal: 380 },
    { name: "Şöbiyet", description: "Kaymaklı şöbiyet", price: 190, tags: ["vejeteryan"], kcal: 410 },
  ],
  hamur_isi_tatlilar: [
    { name: "Tulumba", description: "Şerbetli tulumba", price: 120, tags: ["vejeteryan"], kcal: 350 },
    { name: "Lokma", description: "Sıcak lokma", price: 110, tags: ["vejeteryan"], kcal: 320 },
  ],
  meyveli_tatlilar: [
    { name: "Meyve Salatası", description: "Mevsim meyveleri", price: 130, tags: ["vegan", "glutensiz", "seker_ilavesiz"], kcal: 55 },
    { name: "Çilekli Magnolia", description: "Çilek magnolia", price: 160, tags: ["vejeteryan"], kcal: 210 },
  ],
  dondurmalar: [
    { name: "Dondurma 2 Top", description: "İki top dondurma", price: 100, tags: ["vejeteryan", "glutensiz", "populer"], kcal: 180 },
    { name: "Maraş Dondurma", description: "Geleneksel Maraş dondurması", price: 140, tags: ["vejeteryan", "glutensiz"], kcal: 200 },
    { name: "Çikolata Top", description: "Tek top bitter çikolata dondurma", price: 70, tags: ["vejeteryan", "glutensiz"], kcal: 190 },
  ],
  soguk_tatlilar: [
    { name: "Magnolia", description: "Bisküvili soğuk magnolia", price: 160, tags: ["vejeteryan"], kcal: 220 },
    { name: "Tiramisu", description: "Klasik soğuk tiramisu", price: 180, tags: ["vejeteryan", "populer"], kcal: 280 },
    { name: "Panna Cotta", description: "Vanilyalı panna cotta", price: 150, tags: ["vejeteryan", "glutensiz"], kcal: 190 },
  ],
  cikolatali_tatlilar: [
    { name: "Çikolatalı Sufle", description: "Sıcak çikolata suflesi", price: 190, tags: ["vejeteryan", "sef_ozel"], kcal: 320 },
    { name: "Brownie", description: "Cevizli brownie", price: 150, tags: ["vejeteryan", "populer"], kcal: 380 },
    { name: "Profiterol", description: "Çikolata soslu profiterol", price: 160, tags: ["vejeteryan"], kcal: 290 },
    { name: "Çikolatalı Pasta Dilim", description: "Yoğun çikolata pasta", price: 170, tags: ["vejeteryan"], kcal: 340 },
  ],
  pasta_cesitleri: [
    { name: "Cheesecake", description: "Frambuazlı cheesecake", price: 180, tags: ["vejeteryan", "populer"], kcal: 310 },
    { name: "Orman Meyveli Pasta", description: "Kremalı orman meyveli dilim", price: 175, tags: ["vejeteryan"], kcal: 290 },
  ],
  kek_cesitleri: [
    { name: "Havuçlu Kek", description: "Cevizli havuç kek", price: 140, tags: ["vejeteryan"], kcal: 280 },
    { name: "Mozaik Kek", description: "Bitter çikolatalı mozaik", price: 130, tags: ["vejeteryan"], kcal: 300 },
    { name: "Limonlu Kek", description: "Taze limonlu kek dilimi", price: 135, tags: ["vejeteryan"], kcal: 260 },
  ],
  serpme_kahvalti: [
    { name: "Serpme Kahvaltı (2 Kişilik)", description: "Zengin serpme kahvaltı", price: 650, tags: ["vejeteryan", "populer"], kcal: 450, servesPeopleMin: 2, servesPeopleMax: 2 },
    { name: "Tek Kişilik Kahvaltı", description: "Standart kahvaltı tabağı", price: 280, tags: ["vejeteryan"], kcal: 380, servesPeopleMin: 1, servesPeopleMax: 1 },
  ],
  omlet_ve_yumurta_cesitleri: [
    { name: "Menemen", description: "Domatesli menemen", price: 160, tags: ["vejeteryan", "glutensiz", "populer"], kcal: 124 },
    { name: "Kaşarlı Omlet", description: "Üç yumurta omlet", price: 150, tags: ["vejeteryan", "glutensiz"], kcal: 190 },
    { name: "Sahanda Yumurta", description: "Tereyağlı sahanda", price: 120, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
  ],
  kahvaltilik_tatlilar: [
    { name: "Bal Kaymak", description: "Petek bal ve kaymak", price: 180, tags: ["vejeteryan", "glutensiz"], kcal: 320 },
    { name: "Reçel Tabağı", description: "Üç çeşit reçel", price: 90, tags: ["vejeteryan", "vegan"], kcal: 210 },
  ],
  gozleme: [
    { name: "Peynirli Gözleme", description: "İnce hamur peynirli", price: 140, tags: ["vejeteryan", "populer"], kcal: 250 },
    { name: "Patatesli Gözleme", description: "Baharatlı patates", price: 130, tags: ["vegan"], kcal: 230 },
    { name: "Ispanaklı Gözleme", description: "Ispanak ve peynir", price: 145, tags: ["vejeteryan"], kcal: 240 },
  ],
  ekmek_ve_hamur_isleri: [
    { name: "Tandır Ekmeği", description: "Sıcak tandır", price: 40, tags: ["vegan"], kcal: 250 },
    { name: "Poğaça", description: "Peynirli poğaça", price: 50, tags: ["vejeteryan"], kcal: 280 },
  ],
  soslar: [
    { name: "Sarımsaklı Mayonez", description: "Ekstra sos", price: 25, tags: ["vejeteryan", "glutensiz"], kcal: 450 },
    { name: "Acı Sos", description: "Ev yapımı acı sos", price: 25, tags: ["vegan", "acili", "glutensiz"], kcal: 40 },
  ],
  garniturler: [
    { name: "Pilav Porsiyon", description: "Yan pilav", price: 70, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
    { name: "Izgara Sebze", description: "Mevsim ızgara sebze", price: 90, tags: ["vegan", "glutensiz"], kcal: 60 },
  ],
  et_durum: [
    { name: "Et Dürüm", description: "Lavaşta et döner", price: 280, tags: ["populer"], kcal: 260 },
    { name: "Et Durum Extra", description: "Bol et dürüm", price: 340, tags: [], kcal: 310 },
  ],
  tavuk_durum: [
    { name: "Tavuk Dürüm", description: "Lavaşta tavuk döner", price: 240, tags: ["populer"], kcal: 230 },
    { name: "Tavuk Durum Acılı", description: "Acılı tavuk dürüm", price: 250, tags: ["acili"], kcal: 235 },
  ],
  doner_porsiyon: [
    { name: "Et Döner Porsiyon", description: "Pilav üstü et döner", price: 320, tags: ["populer"], kcal: 280 },
    { name: "Tavuk Döner Porsiyon", description: "Pilav üstü tavuk", price: 280, tags: [], kcal: 250 },
  ],
  iskender: [
    { name: "İskender", description: "Tereyağlı iskender", price: 380, tags: ["populer", "sef_ozel"], kcal: 320 },
    { name: "Tavuk İskender", description: "Tavuklu iskender", price: 340, tags: [], kcal: 290 },
  ],
  kirmizi_et_izgara: [
    { name: "Antrikot", description: "300g antrikot", price: 620, tags: ["glutensiz", "sef_ozel"], kcal: 250 },
    { name: "Bonfile", description: "250g bonfile", price: 680, tags: ["glutensiz"], kcal: 220 },
    { name: "Pirzola", description: "Kuzu pirzola", price: 590, tags: ["glutensiz"], kcal: 270 },
  ],
  tavuk_izgara: [
    { name: "Izgara Tavuk Pirzola", description: "Baharatlı tavuk", price: 300, tags: ["glutensiz"], kcal: 165 },
    { name: "Kanat Izgara", description: "Acılı kanat", price: 260, tags: ["acili"], kcal: 210 },
  ],
  karisik_izgara: [
    { name: "Karışık Izgara", description: "Köfte, şiş, kanat", price: 520, tags: ["populer", "glutensiz"], kcal: 260 },
    { name: "Special Karışık", description: "Bonfile dahil karışık", price: 720, tags: ["sef_ozel"], kcal: 280 },
  ],
  balik_cesitleri: [
    { name: "Levrek Izgara", description: "Bütün levrek", price: 480, tags: ["glutensiz", "laktozsuz"], kcal: 140 },
    { name: "Çipura Izgara", description: "Bütün çipura", price: 460, tags: ["glutensiz", "laktozsuz"], kcal: 145 },
    { name: "Somon Izgara", description: "Norveç somon", price: 520, tags: ["glutensiz"], kcal: 180 },
  ],
  kabuklu_deniz_urunleri: [
    { name: "Karides Güveç", description: "Kaşarlı karides", price: 420, tags: ["sef_ozel"], kcal: 160 },
    { name: "Midye Dolma", description: "10 adet midye", price: 200, tags: ["vegan"], kcal: 120 },
  ],
  meze_tarzi_deniz_urunleri: [
    { name: "Ahtapot Salata", description: "Zeytinyağlı ahtapot", price: 320, tags: ["laktozsuz", "glutensiz"], kcal: 110 },
    { name: "Füme Somon", description: "Kapari ve limon", price: 280, tags: ["laktozsuz", "glutensiz"], kcal: 160 },
  ],
  patates_kizartmasi_cesitleri: [
    { name: "Patates Kızartması", description: "Klasik patates", price: 110, tags: ["vegan", "populer"], kcal: 280 },
    { name: "Baharatlı Patates", description: "Çeşnili patates", price: 130, tags: ["vegan", "acili"], kcal: 290 },
    { name: "Truffle Patates", description: "Trüf yağlı", price: 180, tags: ["vegan", "sef_ozel"], kcal: 300 },
  ],
  kizartmalar: [
    { name: "Soğan Halkası", description: "Çıtır soğan halkası", price: 120, tags: ["vejeteryan"], kcal: 310 },
    { name: "Mozzarella Stick", description: "6 adet", price: 160, tags: ["vejeteryan"], kcal: 340 },
  ],
  cips_ve_nachos: [
    { name: "Nachos", description: "Cheddar ve salsa", price: 190, tags: ["vejeteryan", "acili"], kcal: 320 },
    { name: "Cips Tabağı", description: "Karışık cips", price: 100, tags: ["vegan"], kcal: 400 },
  ],
  alkollu_kokteyller: [
    { name: "Mojito", description: "Nane, lime, rom", price: 280, tags: ["populer"], basis: "PER_100ML", kcal: 95 },
    { name: "Margarita", description: "Tekila, triple sec", price: 290, tags: [], basis: "PER_100ML", kcal: 110 },
    { name: "Gin Tonic", description: "Gin ve tonic", price: 270, tags: [], basis: "PER_100ML", kcal: 80 },
  ],
  alkolsuz_kokteyller: [
    { name: "Virgin Mojito", description: "Alkolsüz mojito", price: 160, tags: ["vegan", "seker_ilavesiz"], basis: "PER_100ML", kcal: 35 },
    { name: "Fruit Punch", description: "Karışık meyve punch", price: 150, tags: ["vegan"], basis: "PER_100ML", kcal: 48 },
  ],
  noodle_cesitleri: [
    { name: "Pad Thai", description: "Karidesli pad thai", price: 320, tags: ["acili", "yeni"], kcal: 220 },
    { name: "Sebzeli Noodle", description: "Wok sebze noodle", price: 250, tags: ["vegan"], kcal: 180 },
  ],
  sushi: [
    { name: "California Roll", description: "8 parça", price: 260, tags: ["laktozsuz", "populer"], kcal: 155 },
    { name: "Somom Roll", description: "8 parça somon", price: 290, tags: ["laktozsuz"], kcal: 160 },
    { name: "Vegan Roll", description: "Avokado ve salatalık", price: 230, tags: ["vegan", "laktozsuz"], kcal: 140 },
  ],
  wok_yemekleri: [
    { name: "Tavuk Wok", description: "Sebzeli tavuk wok", price: 280, tags: ["acili"], kcal: 190 },
    { name: "Dana Wok", description: "Biberli dana wok", price: 340, tags: ["acili", "sef_ozel"], kcal: 210 },
  ],
  ramen: [
    { name: "Shoyu Ramen", description: "Soya bazlı ramen", price: 300, tags: ["yeni"], kcal: 220 },
    { name: "Spicy Miso Ramen", description: "Acılı miso ramen", price: 320, tags: ["acili", "yeni"], kcal: 240 },
  ],
  cocuk_ana_yemekleri: [
    { name: "Çocuk Köfte", description: "Küçük porsiyon köfte", price: 180, tags: ["glutensiz"], kcal: 200 },
    { name: "Çocuk Nugget", description: "Patates ile nugget", price: 170, tags: [], kcal: 260 },
    { name: "Çocuk Makarna", description: "Tereyağlı makarna", price: 150, tags: ["vejeteryan"], kcal: 210 },
  ],
  cocuk_tatlilari: [
    { name: "Çocuk Dondurma", description: "Tek top dondurma", price: 70, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
    { name: "Mini Pancake", description: "Çikolata soslu", price: 110, tags: ["vejeteryan"], kcal: 240 },
  ],
  cocuk_icecekleri: [
    { name: "Çocuk Süt", description: "Sıcak/soğuk süt", price: 45, tags: ["vejeteryan", "glutensiz"], basis: "PER_100ML", kcal: 60 },
    { name: "Meyveli Soda", description: "Alkolsüz meyve soda", price: 55, tags: ["vegan"], basis: "PER_100ML", kcal: 40 },
  ],
  risotto: [
    { name: "Mantarlı Risotto", description: "Parmesanlı risotto", price: 280, tags: ["vejeteryan", "glutensiz"], kcal: 210 },
    { name: "Deniz Mahsullü Risotto", description: "Karides ve midye", price: 360, tags: ["sef_ozel", "glutensiz"], kcal: 230 },
  ],
  lazanya_ve_firin_makarna: [
    { name: "Lazanya", description: "Kıymalı lazanya", price: 290, tags: ["populer"], kcal: 250 },
    { name: "Fırın Makarna", description: "Beşamel soslu", price: 250, tags: ["vejeteryan"], kcal: 240 },
  ],
  antipasti: [
    { name: "Bruschetta", description: "Domatesli bruschetta", price: 160, tags: ["vegan"], kcal: 180 },
    { name: "Caprese", description: "Mozzarella ve domates", price: 190, tags: ["vejeteryan", "glutensiz"], kcal: 160 },
  ],
  taco: [
    { name: "Et Taco (3 Adet)", description: "Baharatlı et taco", price: 280, tags: ["acili", "yeni"], kcal: 230 },
    { name: "Tavuk Taco (3 Adet)", description: "Izgara tavuk taco", price: 260, tags: ["acili"], kcal: 210 },
  ],
  burrito_ve_quesadilla: [
    { name: "Burrito", description: "Etli burrito", price: 300, tags: ["acili", "populer"], kcal: 280 },
    { name: "Quesadilla", description: "Peynirli quesadilla", price: 240, tags: ["vejeteryan"], kcal: 260 },
  ],
  fajita: [
    { name: "Tavuk Fajita", description: "Biberli tavuk fajita", price: 320, tags: ["glutensiz", "acili"], kcal: 200 },
    { name: "Et Fajita", description: "Dana fajita", price: 360, tags: ["glutensiz", "acili"], kcal: 230 },
  ],
  steak_cesitleri: [
    { name: "Ribeye Steak", description: "350g ribeye", price: 890, tags: ["glutensiz", "sef_ozel"], kcal: 290 },
    { name: "T-Bone Steak", description: "450g T-bone", price: 980, tags: ["glutensiz"], kcal: 300 },
    { name: "Filet Mignon", description: "220g filet", price: 920, tags: ["glutensiz", "populer"], kcal: 230 },
  ],
  steak_yanlari: [
    { name: "Fırın Patates", description: "Steak yanı", price: 90, tags: ["vegan", "glutensiz"], kcal: 150 },
    { name: "Izgara Mantar", description: "Tereyağlı mantar", price: 100, tags: ["vejeteryan", "glutensiz"], kcal: 90 },
  ],
};

const allSubSlugs = taxonomy.mains.flatMap((m) => m.subs.map((s) => s.slug));
const missing = allSubSlugs.filter((s) => !catalog[s] || catalog[s].length === 0);
if (missing.length) {
  console.error("Missing catalog for subs:", missing);
  process.exit(1);
}

const inferServes = (name, item) => {
  if (item.servesPeopleMin != null && item.servesPeopleMax != null) {
    return { servesPeopleMin: item.servesPeopleMin, servesPeopleMax: item.servesPeopleMax };
  }
  const lower = String(name).toLowerCase();
  if (lower.includes("tek kişilik") || lower.includes("tek kisilik")) {
    return { servesPeopleMin: 1, servesPeopleMax: 1 };
  }
  const match = String(name).match(/\((\d+)\s*ki[sş]ilik\)/i);
  if (match) {
    const n = Number(match[1]);
    return { servesPeopleMin: n, servesPeopleMax: n };
  }
  return { servesPeopleMin: 1, servesPeopleMax: 1 };
};

const products = [];
let sortOrder = 1;
for (const slug of allSubSlugs) {
  for (const item of catalog[slug]) {
    const basis = item.basis || "PER_100G";
    const kcal = item.kcal ?? 150;
    const serves = inferServes(item.name, item);
    products.push({
      name: item.name,
      description: item.description,
      price: item.price,
      currency: "TRY",
      subCategorySlug: slug,
      tagSlugs: item.tags || [],
      sortOrder: sortOrder++,
      available: true,
      servesPeopleMin: serves.servesPeopleMin,
      servesPeopleMax: serves.servesPeopleMax,
      nutrition: nutrition(basis, kcal, item.n || {}),
    });
  }
}

const doc = { version: 2, menuId: MENU_ID, products };
const jsonPath = path.join(root, "src/main/resources/seed/menu-products.json");
fs.writeFileSync(jsonPath, JSON.stringify(doc, null, 2) + "\n");

const esc = (s) => String(s).replace(/'/g, "''");
const sqlValues = products
  .map((p) => {
    const tags = `ARRAY[${(p.tagSlugs || []).map((t) => `'${esc(t)}'`).join(",")}]::text[]`;
    const nutr = JSON.stringify(p.nutrition).replace(/'/g, "''");
    return `    ('${esc(p.name)}', '${esc(p.description)}', ${p.price}::numeric, 'TRY', '${p.subCategorySlug}', ${p.sortOrder}, TRUE,\n     '${nutr}'::jsonb,\n     ${tags}, ${p.servesPeopleMin}, ${p.servesPeopleMax})`;
  })
  .join(",\n");

const sql = `-- Auto-generated from scripts/generate_menu_products_seed.js
-- Target menu_id = ${MENU_ID}. Idempotent by (menu_id, lower(name)).

DO $$
DECLARE
    v_menu_id BIGINT := ${MENU_ID};
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tbl_menu WHERE menu_id = v_menu_id AND is_deleted = FALSE) THEN
        RAISE EXCEPTION 'Menu % not found or deleted', v_menu_id;
    END IF;
END $$;

WITH seed(name, description, price, currency, sub_slug, sort_order, available, nutrition, tag_slugs, serves_people_min, serves_people_max) AS (
    VALUES
${sqlValues}
),
ins AS (
    INSERT INTO tbl_menu_products (
        menu_id, name, description, price, currency, sub_category_id,
        sort_order, available, nutrition, serves_people_min, serves_people_max,
        created_at, updated_at, is_deleted
    )
    SELECT
        ${MENU_ID},
        s.name,
        s.description,
        s.price,
        s.currency,
        sc.id,
        s.sort_order,
        s.available,
        s.nutrition,
        s.serves_people_min,
        s.serves_people_max,
        NOW(),
        NOW(),
        FALSE
    FROM seed s
    JOIN tbl_sub_category sc ON sc.slug = s.sub_slug AND sc.is_deleted = FALSE
    WHERE NOT EXISTS (
        SELECT 1
        FROM tbl_menu_products p
        WHERE p.menu_id = ${MENU_ID}
          AND p.is_deleted = FALSE
          AND LOWER(p.name) = LOWER(s.name)
    )
    RETURNING product_id, name
)
INSERT INTO tbl_menu_product_tag (product_id, tag_id)
SELECT i.product_id, t.id
FROM ins i
JOIN seed s ON LOWER(s.name) = LOWER(i.name)
JOIN LATERAL unnest(s.tag_slugs) AS tag_slug ON TRUE
JOIN tbl_menu_tag t ON t.slug = tag_slug AND t.is_deleted = FALSE
ON CONFLICT (product_id, tag_id) DO NOTHING;
`;

fs.writeFileSync(path.join(root, "src/main/resources/seed/menu-products.sql"), sql);
console.log(`Wrote ${products.length} products for ${allSubSlugs.length} subcategories`);
