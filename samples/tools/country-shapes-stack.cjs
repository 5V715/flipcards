// Turns shapes.json (from country-shapes-svg.mjs) into samples/country-shapes.flipcards.json,
// with the country names in English, German, French and Spanish.
const path = require('path');
const OUT = path.join(__dirname, '..', 'country-shapes.flipcards.json');
const fs = require('fs');
const { LANGUAGES: L, countryNames } = require('./country-names.cjs');
const shapes=JSON.parse(fs.readFileSync('shapes.json'));
const rows=shapes.map(s=>({s,v:countryNames(s.a2)}))
 .sort((a,b)=>a.v.en.localeCompare(b.v.en));
const images={};const cards=rows.map(({s,v})=>{const id=s.a2.toLowerCase();images["shape-"+id]="data:image/svg+xml;base64,"+Buffer.from(s.svg).toString('base64');
 return {id:"card-"+id,front:{imageId:"shape-"+id},back:{text:{type:"translated",values:v}}};});
const file={format:"flipcards-stack",version:1,stack:{id:"sample-country-shapes",name:"Country shapes",languages:L,cards},images};
fs.writeFileSync(OUT,JSON.stringify(file,null,2)+"\n");
console.log(cards.length, Math.round(fs.statSync(OUT).size/1024)+"KB");
