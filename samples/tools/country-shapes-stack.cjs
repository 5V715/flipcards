// Turns shapes.json (from country-shapes-svg.mjs) into samples/country-shapes.flipcards.json,
// with the country names in English, German, French and Spanish.
const path = require('path');
const OUT = path.join(__dirname, '..', 'country-shapes.flipcards.json');
const fs=require('fs');const c=require("i18n-iso-countries");const L=["en","de","fr","es"];
for(const l of L)c.registerLocale(require("i18n-iso-countries/langs/"+l+".json"));
const fix={
AE:{en:"United Arab Emirates",fr:"Émirats arabes unis"},BN:{en:"Brunei",de:"Brunei",fr:"Brunei",es:"Brunéi"},
CD:{en:"DR Congo",de:"Demokratische Republik Kongo",es:"República Democrática del Congo"},
CG:{en:"Republic of the Congo",es:"República del Congo"},CF:{fr:"République centrafricaine"},CI:{fr:"Côte d'Ivoire"},
CZ:{fr:"Tchéquie",es:"Chequia"},DO:{fr:"République dominicaine"},GB:{en:"United Kingdom",de:"Vereinigtes Königreich"},
GM:{en:"Gambia"},GW:{es:"Guinea-Bisáu"},IR:{es:"Irán"},KP:{es:"Corea del Norte"},KR:{en:"South Korea",es:"Corea del Sur"},
LA:{en:"Laos",es:"Laos"},MD:{en:"Moldova",de:"Moldau"},NL:{en:"Netherlands"},SA:{fr:"Arabie saoudite"},
SY:{en:"Syria",de:"Syrien",es:"Siria"},SZ:{fr:"Eswatini"},TZ:{fr:"Tanzanie"},US:{fr:"États-Unis"},
TL:{de:"Osttimor",fr:"Timor oriental",es:"Timor Oriental"},FJ:{es:"Fiyi"},BJ:{es:"Benín"},BW:{es:"Botsuana"},
};
const shapes=JSON.parse(fs.readFileSync('shapes.json'));
const rows=shapes.map(s=>{const v={};for(const l of L)v[l]=(fix[s.a2]||{})[l]||c.getName(s.a2,l,{select:"alias"});return {s,v};})
 .sort((a,b)=>a.v.en.localeCompare(b.v.en));
const images={};const cards=rows.map(({s,v})=>{const id=s.a2.toLowerCase();images["shape-"+id]="data:image/svg+xml;base64,"+Buffer.from(s.svg).toString('base64');
 return {id:"card-"+id,front:{imageId:"shape-"+id},back:{text:{type:"translated",values:v}}};});
const file={format:"flipcards-stack",version:1,stack:{id:"sample-country-shapes",name:"Country shapes",languages:L,cards},images};
fs.writeFileSync(OUT,JSON.stringify(file,null,2)+"\n");
console.log(cards.length, Math.round(fs.statSync(OUT).size/1024)+"KB");
