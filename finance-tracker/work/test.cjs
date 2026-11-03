const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const els={};
function el(){return {value:'',hidden:false,dataset:{},elements:{date:{},description:{},amount:{}},children:[],add(x){this.children.push(x)},replaceChildren(){this.children=[]},append(x){this.children.push(x)},reportValidity(){return true}}}
const requests=[];
const context={console,URL,URLSearchParams,TextEncoder,AbortSignal,Option:function(t,v){this.text=t;this.value=v},btoa:s=>Buffer.from(s,'binary').toString('base64'),localStorage:{getItem:()=>null,setItem(){}},document:{getElementById:id=>els[id]??=(el()),querySelectorAll:()=>[],createElement:()=>({createTHead:()=>({insertRow:()=>({append(){}})}),createTBody:()=>({insertRow:()=>({insertCell:()=>({})})})})},fetch:async(url,opts)=>{requests.push({url,opts});return{ok:true,text:async()=>url.endsWith('/categories')?'["FOOD"]':'[]'}},FormData:class{get(k){return{description:' Lunch ',amount:'12.5',category:'FOOD',date:'2026-11-03',type:'EXPENSE'}[k]}}};
vm.createContext(context);vm.runInContext(fs.readFileSync('outputs/finance-tracker/app.js','utf8'),context);
(async()=>{
 context.document.getElementById('username').value='test';context.document.getElementById('password').value='secret';
 await vm.runInContext("run(async()=>{const rows=await api('/transactions',{auth:{username:'test',password:'secret'}});render(rows)})",context);
 assert.equal(requests[0].opts.headers.Authorization,'Basic dGVzdDpzZWNyZXQ=');assert.equal(requests[0].url,'http://localhost:8080/transactions');
 await vm.runInContext("api('/auth/register',{method:'POST',auth:null,body:{username:'a',password:'b'}})",context);
 assert.equal(requests[1].opts.headers.Authorization,undefined);assert.equal(requests[1].opts.method,'POST');
 await vm.runInContext("credentials={username:'test',password:'secret'}",context);
 els.create.onsubmit({preventDefault(){}});await new Promise(r=>setTimeout(r,30));
 const post=requests.find(r=>r.url.endsWith('/transactions')&&r.opts.method==='POST');
 assert.deepEqual(JSON.parse(post.opts.body),{description:'Lunch',amount:12.5,category:'FOOD',date:'2026-11-03',type:'EXPENSE'});
 await assert.rejects(()=>vm.runInContext("api('/transactions',{auth:{username:'a:b',password:'x'}})",context),/colon/);
 console.log('Passed: Basic authorization, registration without credentials, exact create DTO, list array, invalid username.');
})().catch(e=>{console.error(e);process.exitCode=1});
