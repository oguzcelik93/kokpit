package io.github.yorulmazsinan.kokpit.panel

/** The phone panel (one page, five tabs). [token] is hex only, so it is safe inside the script. */
fun panelPage(token: String): String {
    require(token.all { it in '0'..'9' || it in 'a'..'f' })
    return """
<!doctype html><html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0b0d0a"><title>Kokpit paneli</title>
<style>
:root{--bg:#0b0d0a;--card:#141a10;--line:#2c3a1e;--fg:#d8eec4;--dim:#8fa77a;--acc:#8dd443}
*{box-sizing:border-box}body{font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);margin:0;padding:0 12px 60px}
header{position:sticky;top:0;background:var(--bg);padding:10px 0 6px;z-index:2}h1{font-size:19px;color:#a9ec62;margin:0}
nav{display:flex;gap:6px;overflow-x:auto;margin-top:8px}nav button{flex:1;white-space:nowrap;background:var(--card);color:var(--fg);border:1px solid var(--line);padding:10px 8px;font-size:14px}
nav button.on{background:var(--acc);color:#0b0d0a;border-color:var(--acc)}
h2{font-size:16px;margin:0 0 10px}small,.dim{color:var(--dim)}.tab{display:none}.tab.on{display:block}
section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px;margin:12px 0}
button,.btn{background:var(--acc);color:#0b0d0a;border:0;border-radius:10px;padding:12px 16px;font-weight:700;font-size:15px}
button.sec{background:transparent;color:var(--fg);border:1px solid var(--line)}button.del{background:transparent;color:#ff8a80;border:1px solid #5a2a26}
input,select{width:100%;background:#0f130c;color:var(--fg);border:1px solid var(--line);border-radius:10px;padding:12px;font-size:16px;margin:4px 0 8px}
input[type=range]{padding:0}input[type=checkbox]{width:22px;height:22px;margin:0}
.sw{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--line)}.sw:last-child{border:0}
.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.ph{border:2px solid var(--line);border-radius:10px;overflow:hidden;background:#000}
.ph.sel{border-color:var(--acc)}.ph img{width:100%;height:110px;object-fit:cover;display:block}.ph .row{display:flex;gap:4px;padding:6px}
.ph .row button{flex:1;padding:8px 4px;font-size:13px}.row2{display:flex;gap:8px}.row2>*{flex:1}
.li{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:9px 0;border-bottom:1px solid var(--line)}.li button{padding:7px 10px;font-size:13px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;text-align:center}.stats b{display:block;font-size:18px;color:#a9ec62}
#msg{position:fixed;left:12px;right:12px;bottom:12px;background:#22301a;border:1px solid var(--acc);border-radius:12px;padding:12px;display:none;z-index:3}
textarea{width:100%;height:50vh;background:#0f130c;color:var(--fg);border:1px solid var(--line);border-radius:10px;font:12px monospace}
</style></head><body>
<header><h1>Kokpit paneli</h1><small id="info"></small>
<nav id="nav" style="display:none"><button data-t="foto" class="on">Fotoğraf</button><button data-t="ayar">Ayarlar</button><button data-t="defter">Defter</button><button data-t="arac">Araç</button><button data-t="sistem">Sistem</button></nav></header>

<div class="tab on" id="pin"><section><h2 id="pinh">PIN</h2><p class="dim" id="pinp"></p>
<input id="pinv" type="password" inputmode="numeric" maxlength="4" autocomplete="off" placeholder="••••" style="font-size:28px;letter-spacing:12px;text-align:center">
<button onclick="pinGo()" style="width:100%">Devam</button><p class="dim" id="pins"></p></section></div>

<div class="tab" id="foto"><section><h2>Fotoğraflar</h2>
<label class="btn" style="display:block;text-align:center">Fotoğraf ekle<input id="up" type="file" accept="image/*" multiple style="display:none"></label>
<p class="dim" id="upst"></p><div class="grid" id="grid"></div></section></div>

<div class="tab" id="ayar">
<section><h2>Parlaklık</h2><input id="bri" type="range" min="10" max="255"><small class="dim">Kaydırınca ünitede hemen değişir.</small>
<div class="sw"><span>Otomatik gece/gündüz</span><input type="checkbox" data-s="autoBrightness"></div>
<label class="dim">Gündüz seviyesi</label><input type="range" min="10" max="255" data-s="dayBrightness">
<label class="dim">Gece seviyesi</label><input type="range" min="10" max="255" data-s="nightBrightness">
<div class="sw"><span>Gün batınca Night Panel</span><input type="checkbox" data-s="autoNightPanel"></div></section>
<section><h2>Görünüm</h2><label class="dim">Tema</label><select data-s="themeMode"><option value="DARK">Koyu</option><option value="LIGHT">Açık</option><option value="AUTO">Otomatik</option></select>
<label class="dim">Renk</label><select data-s="accent"><option value="SAAB_93">Klasik Kokpit (yeşil)</option><option value="GAUGE_GREEN">Gösterge yeşili</option><option value="CHROME">Beyaz ve krom</option><option value="NIGHT_BLUE">Gece mavisi</option><option value="TEAL">Turkuaz</option></select>
<div class="sw"><span>Bekleme ekranı (büyük saat)</span><input type="checkbox" data-s="idleScreen"></div>
<label class="dim">Bekleme süresi</label><select data-s="idleMinutes"><option value="1">1 dk</option><option value="2">2 dk</option><option value="5">5 dk</option><option value="10">10 dk</option></select></section>
<section><h2>Kontak ve açılış</h2>
<label class="dim">Karşılama videosu</label><select data-s="introMode"><option value="SEASONAL">Mevsime göre (yaz/kış)</option><option value="SELECTED">Seçili video</option><option value="SEQUENTIAL">Sırayla</option><option value="RANDOM">Rastgele</option><option value="OFF">Kapalı</option></select>
<div class="sw"><span>Video sesli</span><input type="checkbox" data-s="introSound"></div>
<div class="sw"><span>Her açılışta video</span><input type="checkbox" data-s="introEveryWake"></div>
<label class="btn sec" style="display:block;text-align:center;margin-top:8px">Kendi videonu ekle (MP4, ≤20 MB)<input id="vid" type="file" accept="video/mp4" style="display:none"></label><p class="dim" id="vst"></p>
<div class="sw"><span>Kontak açılınca müziği sürdür</span><input type="checkbox" data-s="autoResume"></div>
<div class="sw"><span>Kontak açılınca Kokpit öne gelsin</span><input type="checkbox" data-s="wakeToKokpit"></div>
<div class="sw"><span>Ana ekranı koru</span><input type="checkbox" data-s="keepHome"></div>
<div class="sw"><span>iPhone takılınca CarPlay</span><input type="checkbox" data-s="autoCarPlayOnUsb"></div></section></div>

<div class="tab" id="defter">
<section><h2>Yakıt kaydı</h2><input id="km" inputmode="numeric" placeholder="Kilometre sayacı">
<div class="row2"><input id="lt" inputmode="decimal" placeholder="Litre"><input id="pr" inputmode="decimal" placeholder="Tutar ₺"></div>
<div class="sw"><span>Depo tam dolduruldu</span><input id="full" type="checkbox" checked></div><br><button onclick="fuel()">Kaydet</button><p class="dim" id="fst"></p>
<div id="fuels"></div></section>
<section><h2>Yolculuklar</h2><div class="stats" id="tstats"></div><div id="trips"></div></section></div>

<div class="tab" id="arac">
<section><h2>Ev ve İş</h2><input id="home" placeholder="Ev adresi"><button onclick="place('home')">Evi kaydet</button>
<input id="work" placeholder="İş adresi" style="margin-top:12px"><button onclick="place('work')">İşi kaydet</button></section>
<section><h2>Park yeri</h2><p class="dim" id="park">—</p><a class="btn" id="parkgo" style="display:none" target="_blank" rel="noopener">Haritada aç</a></section>
<section><h2>Bluetooth telefonlar</h2><div id="bt"><p class="dim">Yükleniyor…</p></div></section></div>

<div class="tab" id="sistem">
<section><h2>Güncelleme</h2><p id="upd" class="dim">—</p><button onclick="checkUpd()">Şimdi kontrol et</button>
<div class="sw"><span>Otomatik kontrol et ve indir</span><input type="checkbox" data-s="autoUpdate"></div></section>
<section><h2>Teşhis raporu</h2><button class="sec" onclick="report()">Raporu göster</button>
<div id="rep" style="display:none"><p><button onclick="copyRep()">Kopyala</button></p><textarea id="rt" readonly></textarea></div></section>
<section><button class="del" onclick="closePanel()">Paneli kapat</button><p class="dim">Panel 15 dakika kullanılmazsa kendiliğinden kapanır.</p></section></div>
<div id="msg"></div>
<script>
var T='$token';
var S='',pinMode='',firstPin='';
function u(p){return p+(p.indexOf('?')<0?'?':'&')+'t='+T+(S?'&s='+S:'')}
function needPin(){S='';q('nav').style.display='none';[].forEach.call(document.querySelectorAll('.tab'),function(t){t.classList.toggle('on',t.id==='pin')});
 fetch(u('/api/pin')).then(function(r){return r.json()}).then(function(p){pinMode=p.set?'login':'setup';firstPin='';q('pinv').value='';
 q('pinh').textContent=p.set?'PIN girin':'PIN oluşturun';q('pinp').textContent=p.set?'Aracın sahibinin belirlediği 4 haneli PIN.':'İlk bağlantı: aracınız için 4 haneli bir PIN seçin. Bundan sonra panel her açılışta bu PIN’i soracak.'})}
function pinGo(){var v=q('pinv').value;if(!/^[0-9]{4}${'$'}/.test(v)){q('pins').textContent='4 rakam girin';return}
 if(pinMode==='setup'&&!firstPin){firstPin=v;q('pinv').value='';q('pinh').textContent='PIN’i tekrar girin';q('pins').textContent='';return}
 if(pinMode==='setup'&&firstPin!==v){firstPin='';q('pinv').value='';q('pinh').textContent='PIN oluşturun';q('pins').textContent='İki PIN aynı değil, baştan girin';return}
 post('/api/pin/'+(pinMode==='setup'?'setup':'login'),JSON.stringify({pin:v}),'application/json').then(function(sess){S=sess.trim();q('pins').textContent='';
 q('nav').style.display='flex';[].forEach.call(document.querySelectorAll('.tab'),function(t){t.classList.toggle('on',t.id==='foto')});load()}).catch(function(e){q('pinv').value='';firstPin='';if(pinMode==='setup')q('pinh').textContent='PIN oluşturun';q('pins').textContent=e.message})}
function q(id){return document.getElementById(id)}
function msg(t){var m=q('msg');m.textContent=t;m.style.display='block';clearTimeout(window._m);window._m=setTimeout(function(){m.style.display='none'},3500)}
function post(p,body,type){var h=type?{'Content-Type':type}:{};if(S)h['X-Kokpit-Session']=S;return fetch(u(p),{method:'POST',headers:h,body:body}).then(function(r){return r.text().then(function(t){if(r.status===401){needPin();throw new Error('PIN gerekli')}if(!r.ok)throw new Error(t);return t})})}
function get(p){return fetch(u(p)).then(function(r){if(r.status===401){needPin();throw new Error('PIN gerekli')}if(!r.ok)throw new Error('Panel kapanmış. Ünitede yeniden açın.');return r})}
function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e}
function fmt(n,d){return Number(n).toFixed(d).replace('.',',')}
[].forEach.call(document.querySelectorAll('nav button'),function(b){b.onclick=function(){[].forEach.call(document.querySelectorAll('nav button'),function(x){x.classList.toggle('on',x===b)});
 [].forEach.call(document.querySelectorAll('.tab'),function(t){t.classList.toggle('on',t.id===b.dataset.t)});({ayar:loadSettings,defter:loadBook,arac:loadCar,sistem:loadSys}[b.dataset.t]||load)()}});
function load(){get('/api/state').then(function(r){return r.json()}).then(function(s){
 q('info').textContent='Kokpit '+s.version+' · '+s.time.replace('T',' ');
 q('km').placeholder='Kilometre sayacı'+(s.lastKm?' (son: '+s.lastKm+')':'');
 if(!q('home').value)q('home').value=s.home||'';if(!q('work').value)q('work').value=s.work||'';
 var g=q('grid');g.textContent='';if(!s.photos.length)g.appendChild(el('p','dim','Henüz fotoğraf yok.'));
 s.photos.forEach(function(id){var c=el('div','ph'+(id===s.selected?' sel':''));var i=el('img');i.src=u('/photo/'+id+'.jpg')+'&v='+Date.now();c.appendChild(i);
  var row=el('div','row');[['Göster','select',''],['Döndür','rotate','sec'],['Sil','delete','del']].forEach(function(b){var x=el('button',b[2],b[0]);
  x.onclick=function(){if(b[1]==='delete'&&!confirm('Bu fotoğraf silinsin mi?'))return;post('/api/photo/'+id+'/'+b[1]).then(function(){setTimeout(load,400)}).catch(function(e){msg(e.message)})};row.appendChild(x)});
  c.appendChild(row);g.appendChild(c)})}).catch(function(e){msg(e.message)})}
q('up').onchange=function(e){var files=[].slice.call(e.target.files),st=q('upst'),i=0;(function next(){if(i>=files.length){st.textContent=files.length+' fotoğraf yüklendi ✓';e.target.value='';load();return}
 st.textContent='Yükleniyor '+(i+1)+'/'+files.length+'…';post('/api/photo',files[i]).then(function(){i++;next()}).catch(function(err){st.textContent='Olmadı: '+err.message;i++;next()})})()};
q('vid').onchange=function(e){var f=e.target.files[0];if(!f)return;q('vst').textContent='Yükleniyor…';post('/api/intro',f).then(function(){q('vst').textContent='Video eklendi ✓ (Seçili video modunda seçebilirsiniz)'}).catch(function(err){q('vst').textContent='Olmadı: '+err.message});e.target.value=''};
function setS(k,v){var o={};o[k]=v;post('/api/settings',JSON.stringify(o),'application/json').then(function(){msg('Kaydedildi ✓')}).catch(function(e){msg(e.message)})}
[].forEach.call(document.querySelectorAll('[data-s]'),function(x){x.onchange=function(){var v=x.type==='checkbox'?x.checked:(x.type==='range'||x.dataset.s==='idleMinutes'?Number(x.value):x.value);setS(x.dataset.s,v)}});
function loadSettings(){get('/api/settings').then(function(r){return r.json()}).then(function(s){[].forEach.call(document.querySelectorAll('[data-s]'),function(x){var v=s[x.dataset.s];if(x.type==='checkbox')x.checked=!!v;else x.value=v})}).catch(function(e){msg(e.message)})}
var bt;q('bri').oninput=function(){clearTimeout(bt);var v=Number(q('bri').value);bt=setTimeout(function(){post('/api/brightness',JSON.stringify({value:v}),'application/json').catch(function(e){msg(e.message)})},150)};
function fuel(){var b=JSON.stringify({km:q('km').value,liters:q('lt').value,price:q('pr').value,full:q('full').checked});
 post('/api/fuel',b,'application/json').then(function(){q('fst').textContent='Kaydedildi ✓';['km','lt','pr'].forEach(function(x){q(x).value=''});loadBook();load()}).catch(function(e){q('fst').textContent=e.message})}
function loadBook(){get('/api/fuel').then(function(r){return r.json()}).then(function(list){var d=q('fuels');d.textContent='';list.slice(0,30).forEach(function(f){var li=el('div','li');
 li.appendChild(el('span',null,f.date+' · '+f.km+' km · '+fmt(f.liters,2)+' L · '+fmt(f.price,2)+' ₺'+(f.full?'':' · kısmi')));var x=el('button','del','Sil');
 x.onclick=function(){if(confirm('Bu yakıt kaydı silinsin mi?'))post('/api/fuel/'+f.time+'/delete').then(loadBook).catch(function(e){msg(e.message)})};li.appendChild(x);d.appendChild(li)})});
 get('/api/trips').then(function(r){return r.json()}).then(function(t){var s=q('tstats');s.textContent='';[['Bugün',t.today],['Hafta',t.week],['Ay',t.month],['Toplam',t.total]].forEach(function(a){var c=el('div');c.appendChild(el('b',null,fmt(a[1],a[0]==='Toplam'?0:1)));c.appendChild(el('small',null,a[0]+' km'));s.appendChild(c)});
 var d=q('trips');d.textContent='';t.trips.forEach(function(x){var li=el('div','li');li.appendChild(el('span',null,x.when.replace('T',' ').slice(0,16)+' · '+fmt(x.km,1)+' km · '+x.minutes+' dk'));
 var b=el('button','del','Sil');b.onclick=function(){if(confirm('Bu yolculuk silinsin mi?'))post('/api/trips/'+x.start+'/delete').then(loadBook).catch(function(e){msg(e.message)})};li.appendChild(b);d.appendChild(li)})}).catch(function(e){msg(e.message)})}
function place(k){post('/api/place',JSON.stringify({kind:k,address:q(k).value}),'application/json').then(function(){msg(k==='home'?'Ev kaydedildi ✓':'İş kaydedildi ✓')}).catch(function(e){msg(e.message)})}
function loadCar(){get('/api/park').then(function(r){return r.json()}).then(function(p){if(p.link){q('park').textContent='Son park: '+p.time.replace('T',' ');var a=q('parkgo');a.href=p.link;a.style.display='inline-block'}else q('park').textContent='Henüz kayıt yok.'});
 get('/api/bt').then(function(r){return r.json()}).then(function(list){var d=q('bt');d.textContent='';if(!list.length)d.appendChild(el('p','dim','Android’in gördüğü eşleşmiş telefon yok.'));
 list.forEach(function(p){var li=el('div','li');li.appendChild(el('span',null,(p.connected?'● ':'')+p.name));if(!p.connected){var b=el('button',null,'Bağlan');b.onclick=function(){b.textContent='Bağlanıyor…';
 post('/api/bt/switch',JSON.stringify({address:p.address}),'application/json').then(function(){msg(p.name+' bağlandı ✓');loadCar()}).catch(function(e){msg(e.message);loadCar()})};li.appendChild(b)}d.appendChild(li)})}).catch(function(e){msg(e.message)})}
function loadSys(){loadSettings();get('/api/update').then(function(r){return r.text()}).then(function(t){q('upd').textContent=t})}
function checkUpd(){post('/api/update/check').then(function(){q('upd').textContent='Kontrol ediliyor…';setTimeout(loadSys,4000)}).catch(function(e){msg(e.message)})}
function report(){q('rep').style.display='block';q('rt').value='Yükleniyor…';get('/api/report').then(function(r){return r.text()}).then(function(t){q('rt').value=t})}
function copyRep(){var r=q('rt');r.focus();r.select();r.setSelectionRange(0,r.value.length);var ok=false;try{ok=document.execCommand('copy')}catch(e){}msg(ok?'Kopyalandı ✓':'Metni uzun basıp seçin')}
function closePanel(){post('/api/close').then(function(){document.body.textContent='Panel kapatıldı.'})}
needPin();setInterval(function(){if(S&&q('foto').classList.contains('on'))load()},20000);
</script></body></html>
""".trimIndent()
}
