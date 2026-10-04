// Writes samples/world-capitals.flipcards.json: the capital of every UN member state, in English,
// German, French and Spanish. The front shows the country's name and, where country-shapes-svg.mjs
// drew one, its outline: run that first, in the same folder, so that shapes.json exists.
const fs = require('fs');
const path = require('path');
const { LANGUAGES, countryNames } = require('./country-names.cjs');
const OUT = path.join(__dirname, '..', 'world-capitals.flipcards.json');

// Where a country has more than one capital, the one usually taught: the official capital
// (Sucre, Porto-Novo, Sri Jayawardenepura Kotte), else the seat of government (Mbabane).
const capitals = {
AF:"Kabul",AL:"Tirana",DZ:"Algiers",AD:"Andorra la Vella",AO:"Luanda",AG:"Saint John's",AR:"Buenos Aires",
AM:"Yerevan",AU:"Canberra",AT:"Vienna",AZ:"Baku",BS:"Nassau",BH:"Manama",BD:"Dhaka",BB:"Bridgetown",BY:"Minsk",
BE:"Brussels",BZ:"Belmopan",BJ:"Porto-Novo",BT:"Thimphu",BO:"Sucre",BA:"Sarajevo",BW:"Gaborone",BR:"Brasília",
BN:"Bandar Seri Begawan",BG:"Sofia",BF:"Ouagadougou",BI:"Gitega",CV:"Praia",KH:"Phnom Penh",CM:"Yaoundé",
CA:"Ottawa",CF:"Bangui",TD:"N'Djamena",CL:"Santiago",CN:"Beijing",CO:"Bogotá",KM:"Moroni",CG:"Brazzaville",
CD:"Kinshasa",CR:"San José",CI:"Yamoussoukro",HR:"Zagreb",CU:"Havana",CY:"Nicosia",CZ:"Prague",DK:"Copenhagen",
DJ:"Djibouti",DM:"Roseau",DO:"Santo Domingo",EC:"Quito",EG:"Cairo",SV:"San Salvador",GQ:"Malabo",ER:"Asmara",
EE:"Tallinn",SZ:"Mbabane",ET:"Addis Ababa",FJ:"Suva",FI:"Helsinki",FR:"Paris",GA:"Libreville",GM:"Banjul",
GE:"Tbilisi",DE:"Berlin",GH:"Accra",GR:"Athens",GD:"Saint George's",GT:"Guatemala City",GN:"Conakry",GW:"Bissau",
GY:"Georgetown",HT:"Port-au-Prince",HN:"Tegucigalpa",HU:"Budapest",IS:"Reykjavík",IN:"New Delhi",ID:"Jakarta",
IR:"Tehran",IQ:"Baghdad",IE:"Dublin",IL:"Jerusalem",IT:"Rome",JM:"Kingston",JP:"Tokyo",JO:"Amman",KZ:"Astana",
KE:"Nairobi",KI:"South Tarawa",KP:"Pyongyang",KR:"Seoul",KW:"Kuwait City",KG:"Bishkek",LA:"Vientiane",LV:"Riga",
LB:"Beirut",LS:"Maseru",LR:"Monrovia",LY:"Tripoli",LI:"Vaduz",LT:"Vilnius",LU:"Luxembourg",MG:"Antananarivo",
MW:"Lilongwe",MY:"Kuala Lumpur",MV:"Malé",ML:"Bamako",MT:"Valletta",MH:"Majuro",MR:"Nouakchott",MU:"Port Louis",
MX:"Mexico City",FM:"Palikir",MD:"Chișinău",MC:"Monaco",MN:"Ulaanbaatar",ME:"Podgorica",MA:"Rabat",MZ:"Maputo",
MM:"Naypyidaw",NA:"Windhoek",NR:"Yaren",NP:"Kathmandu",NL:"Amsterdam",NZ:"Wellington",NI:"Managua",NE:"Niamey",
NG:"Abuja",MK:"Skopje",NO:"Oslo",OM:"Muscat",PK:"Islamabad",PW:"Ngerulmud",PA:"Panama City",PG:"Port Moresby",
PY:"Asunción",PE:"Lima",PH:"Manila",PL:"Warsaw",PT:"Lisbon",QA:"Doha",RO:"Bucharest",RU:"Moscow",RW:"Kigali",
KN:"Basseterre",LC:"Castries",VC:"Kingstown",WS:"Apia",SM:"San Marino",ST:"São Tomé",SA:"Riyadh",SN:"Dakar",
RS:"Belgrade",SC:"Victoria",SL:"Freetown",SG:"Singapore",SK:"Bratislava",SI:"Ljubljana",SB:"Honiara",
SO:"Mogadishu",ZA:"Pretoria",SS:"Juba",ES:"Madrid",LK:"Sri Jayawardenepura Kotte",SD:"Khartoum",SR:"Paramaribo",
SE:"Stockholm",CH:"Bern",SY:"Damascus",TJ:"Dushanbe",TZ:"Dodoma",TH:"Bangkok",TL:"Dili",TG:"Lomé",
TO:"Nuku'alofa",TT:"Port of Spain",TN:"Tunis",TR:"Ankara",TM:"Ashgabat",TV:"Funafuti",UG:"Kampala",UA:"Kyiv",
AE:"Abu Dhabi",GB:"London",US:"Washington, D.C.",UY:"Montevideo",UZ:"Tashkent",VU:"Port Vila",VE:"Caracas",
VN:"Hanoi",YE:"Sana'a",ZM:"Lusaka",ZW:"Harare",
};

// Only the names that differ from English.
const translated = {
de: {
Algiers:"Algier",Yerevan:"Jerewan",Vienna:"Wien",Brussels:"Brüssel",Yaoundé:"Jaunde",Santiago:"Santiago de Chile",
Beijing:"Peking",Havana:"Havanna",Nicosia:"Nikosia",Prague:"Prag",Copenhagen:"Kopenhagen",Djibouti:"Dschibuti",
Cairo:"Kairo","Addis Ababa":"Addis Abeba",Tbilisi:"Tiflis",Athens:"Athen","Guatemala City":"Guatemala-Stadt",
"New Delhi":"Neu-Delhi",Tehran:"Teheran",Baghdad:"Bagdad",Rome:"Rom",Tokyo:"Tokio","South Tarawa":"Süd-Tarawa",
Pyongyang:"Pjöngjang","Kuwait City":"Kuwait-Stadt",Bishkek:"Bischkek",Tripoli:"Tripolis",Luxembourg:"Luxemburg",
"Mexico City":"Mexiko-Stadt",Muscat:"Maskat","Panama City":"Panama-Stadt",Warsaw:"Warschau",Lisbon:"Lissabon",
Bucharest:"Bukarest",Moscow:"Moskau",Riyadh:"Riad",Belgrade:"Belgrad",Singapore:"Singapur",Mogadishu:"Mogadischu",
Khartoum:"Khartum",Damascus:"Damaskus",Dushanbe:"Duschanbe",Ashgabat:"Aschgabat",Kyiv:"Kiew",Tashkent:"Taschkent",
"Sana'a":"Sanaa",
},
fr: {
Kabul:"Kaboul",Algiers:"Alger","Andorra la Vella":"Andorre-la-Vieille",Yerevan:"Erevan",Vienna:"Vienne",Baku:"Bakou",
Brussels:"Bruxelles",Thimphu:"Thimphou",Brasília:"Brasilia",Bogotá:"Bogota","N'Djamena":"N'Djaména",Beijing:"Pékin",
Havana:"La Havane",Nicosia:"Nicosie",Copenhagen:"Copenhague","Santo Domingo":"Saint-Domingue",Cairo:"Le Caire",
"Addis Ababa":"Addis-Abeba",Athens:"Athènes","Guatemala City":"Guatemala",Reykjavík:"Reykjavik",Tehran:"Téhéran",
Baghdad:"Bagdad",Jerusalem:"Jérusalem","South Tarawa":"Tarawa-Sud","Kuwait City":"Koweït",Bishkek:"Bichkek",
Beirut:"Beyrouth",Valletta:"La Valette","Mexico City":"Mexico",Chișinău:"Chisinau",Ulaanbaatar:"Oulan-Bator",
Kathmandu:"Katmandou",Muscat:"Mascate","Panama City":"Panama",Manila:"Manille",Warsaw:"Varsovie",Lisbon:"Lisbonne",
Bucharest:"Bucarest",Moscow:"Moscou","San Marino":"Saint-Marin","São Tomé":"Sao Tomé",Riyadh:"Riyad",
Singapore:"Singapour",Mogadishu:"Mogadiscio",Juba:"Djouba",Bern:"Berne",Damascus:"Damas",Dushanbe:"Douchanbé",
Seoul:"Séoul","Port of Spain":"Port-d'Espagne",Ashgabat:"Achgabat",Kyiv:"Kiev","Abu Dhabi":"Abou Dabi",
London:"Londres","Washington, D.C.":"Washington",Tashkent:"Tachkent","Port Vila":"Port-Vila",Hanoi:"Hanoï",
"Sana'a":"Sanaa",
},
es: {
Algiers:"Argel",Yerevan:"Ereván",Vienna:"Viena",Baku:"Bakú",Dhaka:"Daca",Brussels:"Bruselas","Porto-Novo":"Porto Novo",
Thimphu:"Timbu",Brasília:"Brasilia",Sofia:"Sofía",Ouagadougou:"Uagadugú","Phnom Penh":"Nom Pen",Yaoundé:"Yaundé",
"N'Djamena":"Yamena",Santiago:"Santiago de Chile",Beijing:"Pekín",Yamoussoukro:"Yamusukro",Havana:"La Habana",
Prague:"Praga",Copenhagen:"Copenhague",Djibouti:"Yibuti",Cairo:"El Cairo","Addis Ababa":"Adís Abeba",Paris:"París",
Tbilisi:"Tiflis",Berlin:"Berlín",Athens:"Atenas","Guatemala City":"Ciudad de Guatemala","Port-au-Prince":"Puerto Príncipe",
Reykjavík:"Reikiavik","New Delhi":"Nueva Delhi",Jakarta:"Yakarta",Tehran:"Teherán",Baghdad:"Bagdad",Dublin:"Dublín",
Jerusalem:"Jerusalén",Rome:"Roma",Tokyo:"Tokio",Amman:"Amán","South Tarawa":"Tarawa Sur",Pyongyang:"Pionyang",
Seoul:"Seúl","Kuwait City":"Ciudad de Kuwait",Bishkek:"Biskek",Vientiane:"Vientián",Tripoli:"Trípoli",
Luxembourg:"Luxemburgo",Lilongwe:"Lilongüe",Valletta:"La Valeta",Nouakchott:"Nuakchot","Mexico City":"Ciudad de México",
Chișinău:"Chisináu",Monaco:"Mónaco",Ulaanbaatar:"Ulán Bator",Naypyidaw:"Naipyidó",Kathmandu:"Katmandú",
Amsterdam:"Ámsterdam",Abuja:"Abuya",Skopje:"Skopie",Muscat:"Mascate","Panama City":"Ciudad de Panamá",Warsaw:"Varsovia",
Lisbon:"Lisboa",Bucharest:"Bucarest",Moscow:"Moscú","São Tomé":"Santo Tomé",Riyadh:"Riad",Belgrade:"Belgrado",
Singapore:"Singapur",Ljubljana:"Liubliana",Mogadishu:"Mogadiscio",Juba:"Yuba",Khartoum:"Jartum",Stockholm:"Estocolmo",
Bern:"Berna",Damascus:"Damasco",Dushanbe:"Dusambé","Nuku'alofa":"Nukualofa","Port of Spain":"Puerto España",
Tunis:"Túnez",Ashgabat:"Asjabad",Kyiv:"Kiev","Abu Dhabi":"Abu Dabi",London:"Londres","Washington, D.C.":"Washington D. C.",
Tashkent:"Taskent",Hanoi:"Hanói","Sana'a":"Saná",
},
};

const shapes = fs.existsSync('shapes.json') ? JSON.parse(fs.readFileSync('shapes.json')) : [];
const shapeOf = Object.fromEntries(shapes.map(s => [s.a2, s.svg]));

const rows = Object.keys(capitals).map(a2 => {
  const capital = {};
  for (const l of LANGUAGES) capital[l] = (translated[l] || {})[capitals[a2]] || capitals[a2];
  return { a2, country: countryNames(a2), capital };
}).sort((a, b) => a.country.en.localeCompare(b.country.en));

const images = {};
const cards = rows.map(({ a2, country, capital }) => {
  const id = a2.toLowerCase();
  const front = { text: { type: 'translated', values: country } };
  if (shapeOf[a2]) {
    front.imageId = 'shape-' + id;
    images[front.imageId] = 'data:image/svg+xml;base64,' + Buffer.from(shapeOf[a2]).toString('base64');
  }
  return { id: 'card-' + id, front, back: { text: { type: 'translated', values: capital } } };
});
const file = {
  format: 'flipcards-stack', version: 1,
  stack: { id: 'sample-world-capitals', name: 'World capitals', languages: LANGUAGES, cards },
  images,
};
fs.writeFileSync(OUT, JSON.stringify(file, null, 2) + '\n');
console.log(cards.length, 'cards,', Object.keys(images).length, 'outlines,', Math.round(fs.statSync(OUT).size / 1024) + ' KB');
