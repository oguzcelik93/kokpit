package io.github.yorulmazsinan.kokpit.panel

/** The phone panel (one page). [token] is hex only, so it is safe inside the script. */
fun panelPage(token: String): String {
    require(token.all { it in '0'..'9' || it in 'a'..'f' })
    return """
<!doctype html><html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Kokpit paneli</title>
<style>
:root{--bg:#0b0d0a;--card:#141a10;--line:#2c3a1e;--fg:#d8eec4;--dim:#8fa77a;--acc:#8dd443}
*{box-sizing:border-box}body{font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);margin:0;padding:14px 14px 40px}
h1{font-size:21px;color:#a9ec62;margin:4px 0 2px}h2{font-size:17px;margin:0 0 10px}small,.dim{color:var(--dim)}
section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px;margin:14px 0}
button,.btn{background:var(--acc);color:#0b0d0a;border:0;border-radius:10px;padding:12px 16px;font-weight:700;font-size:15px}
button.sec{background:transparent;color:var(--fg);border:1px solid var(--line)}button.del{background:transparent;color:#ff8a80;border:1px solid #5a2a26}
input{width:100%;background:#0f130c;color:var(--fg);border:1px solid var(--line);border-radius:10px;padding:12px;font-size:16px;margin:4px 0 8px}
.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.ph{border:2px solid var(--line);border-radius:10px;overflow:hidden;background:#000}
.ph.sel{border-color:var(--acc)}.ph img{width:100%;height:110px;object-fit:cover;display:block}.ph .row{display:flex;gap:4px;padding:6px}
.ph .row button{flex:1;padding:8px 4px;font-size:13px}.row2{display:flex;gap:8px}.row2>*{flex:1}
#msg{position:fixed;left:12px;right:12px;bottom:12px;background:#22301a;border:1px solid var(--acc);border-radius:12px;padding:12px;display:none}
textarea{width:100%;height:50vh;background:#0f130c;color:var(--fg);border:1px solid var(--line);border-radius:10px;font:12px monospace}
</style></head><body>
<h1>Kokpit paneli</h1><small id="info"></small>

<section><h2>Fotoğraflar</h2>
<label class="btn" style="display:block;text-align:center">Fotoğraf ekle<input id="up" type="file" accept="image/*" multiple style="display:none"></label>
<p class="dim" id="upst"></p><div class="grid" id="grid"></div></section>

<section><h2>Yakıt kaydı</h2>
<input id="km" inputmode="numeric" placeholder="Kilometre sayacı">
<div class="row2"><input id="lt" inputmode="decimal" placeholder="Litre"><input id="pr" inputmode="decimal" placeholder="Tutar ₺"></div>
<label class="dim"><input id="full" type="checkbox" checked style="width:auto"> Depo tam dolduruldu</label><br><br>
<button onclick="fuel()">Kaydet</button><p class="dim" id="fst"></p></section>

<section><h2>Ev ve İş</h2>
<input id="home" placeholder="Ev adresi"><button onclick="place('home')">Evi kaydet</button>
<input id="work" placeholder="İş adresi" style="margin-top:12px"><button onclick="place('work')">İşi kaydet</button></section>

<section><h2>Teşhis raporu</h2><button class="sec" onclick="report()">Raporu göster</button>
<div id="rep" style="display:none"><p><button onclick="copyRep()">Kopyala</button></p><textarea id="rt" readonly></textarea></div></section>

<section><button class="del" onclick="closePanel()">Paneli kapat</button>
<p class="dim">Panel 15 dakika kullanılmazsa kendiliğinden kapanır.</p></section>
<div id="msg"></div>
<script>
var T='$token';
function u(p){return p+(p.indexOf('?')<0?'?':'&')+'t='+T}
function msg(t){var m=document.getElementById('msg');m.textContent=t;m.style.display='block';clearTimeout(window._m);window._m=setTimeout(function(){m.style.display='none'},3500)}
function post(p,body,type){return fetch(u(p),{method:'POST',headers:type?{'Content-Type':type}:{},body:body}).then(function(r){return r.text().then(function(t){if(!r.ok)throw new Error(t);return t})})}
function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text)e.textContent=text;return e}
function load(){fetch(u('/api/state')).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(s){
 document.getElementById('info').textContent='Kokpit '+s.version+' · '+s.time.replace('T',' ')+(s.avgL100?' · ort. '+s.avgL100.toFixed(1)+' L/100km':'');
 document.getElementById('km').placeholder='Kilometre sayacı'+(s.lastKm?' (son: '+s.lastKm+')':'');
 if(!document.getElementById('home').value)document.getElementById('home').value=s.home||'';
 if(!document.getElementById('work').value)document.getElementById('work').value=s.work||'';
 var g=document.getElementById('grid');g.textContent='';
 if(!s.photos.length)g.appendChild(el('p','dim','Henüz fotoğraf yok.'));
 s.photos.forEach(function(id){var c=el('div','ph'+(id===s.selected?' sel':''));var i=el('img');i.src=u('/photo/'+id+'.jpg')+'&v='+Date.now();c.appendChild(i);
  var row=el('div','row');[['Göster','select',''],['Döndür','rotate','sec'],['Sil','delete','del']].forEach(function(b){var x=el('button',b[2],b[0]);
  x.onclick=function(){if(b[1]==='delete'&&!confirm('Bu fotoğraf silinsin mi?'))return;post('/api/photo/'+id+'/'+b[1]).then(function(){setTimeout(load,400)}).catch(function(e){msg(e.message)})};row.appendChild(x)});
  c.appendChild(row);g.appendChild(c)})}).catch(function(){msg('Panel kapanmış. Ünitede yeniden açın.')})}
document.getElementById('up').onchange=function(e){var files=[].slice.call(e.target.files);var st=document.getElementById('upst');var i=0;
 (function next(){if(i>=files.length){st.textContent=files.length+' fotoğraf yüklendi ✓';e.target.value='';load();return}
 st.textContent='Yükleniyor '+(i+1)+'/'+files.length+'…';post('/api/photo',files[i]).then(function(){i++;next()}).catch(function(err){st.textContent='Olmadı: '+err.message;i++;next()})})()};
function fuel(){var b=JSON.stringify({km:document.getElementById('km').value,liters:document.getElementById('lt').value,price:document.getElementById('pr').value,full:document.getElementById('full').checked});
 post('/api/fuel',b,'application/json').then(function(){document.getElementById('fst').textContent='Kaydedildi ✓';['km','lt','pr'].forEach(function(x){document.getElementById(x).value=''});load()}).catch(function(e){document.getElementById('fst').textContent=e.message})}
function place(k){post('/api/place',JSON.stringify({kind:k,address:document.getElementById(k).value}),'application/json').then(function(){msg(k==='home'?'Ev kaydedildi ✓':'İş kaydedildi ✓')}).catch(function(e){msg(e.message)})}
function report(){document.getElementById('rep').style.display='block';document.getElementById('rt').value='Yükleniyor…';fetch(u('/api/report')).then(function(r){return r.text()}).then(function(t){document.getElementById('rt').value=t})}
function copyRep(){var r=document.getElementById('rt');r.focus();r.select();r.setSelectionRange(0,r.value.length);var ok=false;try{ok=document.execCommand('copy')}catch(e){}msg(ok?'Kopyalandı ✓':'Metni uzun basıp seçin')}
function closePanel(){post('/api/close').then(function(){document.body.textContent='Panel kapatıldı.'})}
load();setInterval(load,20000);
</script></body></html>
""".trimIndent()
}
