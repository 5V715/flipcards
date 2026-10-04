// Country names in English, German, French and Spanish, shared by the sample scripts.
// i18n-iso-countries gives the names; some are replaced by the shorter names people use.
const countries = require('i18n-iso-countries');
const LANGUAGES = ['en', 'de', 'fr', 'es'];
for (const l of LANGUAGES) countries.registerLocale(require('i18n-iso-countries/langs/' + l + '.json'));

const fix = {
AE:{en:"United Arab Emirates",fr:"Émirats arabes unis"},BN:{en:"Brunei",de:"Brunei",fr:"Brunei",es:"Brunéi"},
CD:{en:"DR Congo",de:"Demokratische Republik Kongo",es:"República Democrática del Congo"},
CG:{en:"Republic of the Congo",es:"República del Congo"},CF:{fr:"République centrafricaine"},CI:{fr:"Côte d'Ivoire"},
CZ:{fr:"Tchéquie",es:"Chequia"},DO:{fr:"République dominicaine"},GB:{en:"United Kingdom",de:"Vereinigtes Königreich"},
GM:{en:"Gambia"},GW:{es:"Guinea-Bisáu"},IR:{es:"Irán"},KP:{es:"Corea del Norte"},KR:{en:"South Korea",es:"Corea del Sur"},
LA:{en:"Laos",es:"Laos"},MD:{en:"Moldova",de:"Moldau"},NL:{en:"Netherlands"},SA:{fr:"Arabie saoudite"},
SY:{en:"Syria",de:"Syrien",es:"Siria"},SZ:{fr:"Eswatini"},TZ:{fr:"Tanzanie"},US:{fr:"États-Unis"},
TL:{de:"Osttimor",fr:"Timor oriental",es:"Timor Oriental"},FJ:{es:"Fiyi"},BJ:{es:"Benín"},BW:{es:"Botsuana"},
FM:{en:"Micronesia",fr:"Micronésie"},ST:{en:"São Tomé and Príncipe"},
};

/** { en, de, fr, es } for an ISO alpha-2 code such as "AT". */
function countryNames(a2) {
  const names = {};
  for (const l of LANGUAGES) names[l] = (fix[a2] || {})[l] || countries.getName(a2, l, { select: 'alias' });
  return names;
}

module.exports = { LANGUAGES, countryNames };
