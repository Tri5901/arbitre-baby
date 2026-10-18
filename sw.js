var CACHE = 'arbitre-baby-v1';
var ASSETS = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png', './icon-maskable-512.png'];
self.addEventListener('install', function(e){
  e.waitUntil(caches.open(CACHE).then(function(c){ return c.addAll(ASSETS); }).then(function(){ return self.skipWaiting(); }));
});
self.addEventListener('activate', function(e){
  e.waitUntil(caches.keys().then(function(ks){ return Promise.all(ks.filter(function(k){ return k !== CACHE; }).map(function(k){ return caches.delete(k); })); }).then(function(){ return self.clients.claim(); }));
});
self.addEventListener('fetch', function(e){
  if (e.request.method !== 'GET') return;
  var same = new URL(e.request.url).origin === self.location.origin;
  e.respondWith(fetch(e.request).then(function(res){
    if (same && res.ok) { var copy = res.clone(); caches.open(CACHE).then(function(c){ c.put(e.request, copy); }); }
    return res;
  }).catch(function(){
    return caches.match(e.request).then(function(m){ return m || (same ? caches.match('./index.html') : Response.error()); });
  }));
});
