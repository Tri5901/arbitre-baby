// Changer CACHE à chaque livraison qui ajoute ou renomme un fichier de ASSETS
var CACHE = 'arbitre-baby-v2';
var ASSETS = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png', './icon-maskable-512.png',
  './fonts/barlow-500.woff2', './fonts/barlow-600.woff2', './fonts/barlow-700.woff2',
  './fonts/barlow-condensed-600.woff2', './fonts/barlow-condensed-700.woff2', './fonts/barlow-condensed-800.woff2'];
// Au-delà de ce délai sans réponse du réseau, on sert la copie gardée (réseau faible dans une salle)
var DELAI_RESEAU = 2500;

self.addEventListener('install', function(e){
  e.waitUntil(caches.open(CACHE).then(function(c){ return c.addAll(ASSETS); }).then(function(){ return self.skipWaiting(); }));
});
self.addEventListener('activate', function(e){
  e.waitUntil(caches.keys().then(function(ks){ return Promise.all(ks.filter(function(k){ return k !== CACHE; }).map(function(k){ return caches.delete(k); })); }).then(function(){ return self.clients.claim(); }));
});
self.addEventListener('fetch', function(e){
  if (e.request.method !== 'GET') return;
  var url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;
  // version.json sert à détecter une nouvelle version : toujours le réseau, jamais le cache
  if (url.pathname.slice(-12) === 'version.json') return;
  var copie = function(){ return caches.match(e.request).then(function(m){ return m || (e.request.mode === 'navigate' ? caches.match('./index.html') : undefined); }); };
  var reseau = fetch(e.request).then(function(res){
    if (res.ok) { var c = res.clone(); caches.open(CACHE).then(function(cache){ cache.put(e.request, c); }); }
    return res;
  });
  e.respondWith(new Promise(function(ok, ko){
    var fini = false;
    var finir = function(r){ if (!fini && r) { fini = true; ok(r); } };
    // Réseau lent : la copie gardée part au bout du délai, si elle existe
    var minuteur = setTimeout(function(){ copie().then(finir); }, DELAI_RESEAU);
    reseau.then(function(r){ clearTimeout(minuteur); finir(r); }).catch(function(){
      clearTimeout(minuteur);
      copie().then(function(m){ if (m) finir(m); else if (!fini) ko(new Error('hors ligne')); });
    });
  }));
});
