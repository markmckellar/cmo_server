(function(e){function t(t){for(var r,i,s=t[0],c=t[1],u=t[2],v=0,m=[];v<s.length;v++)i=s[v],Object.prototype.hasOwnProperty.call(a,i)&&a[i]&&m.push(a[i][0]),a[i]=0;for(r in c)Object.prototype.hasOwnProperty.call(c,r)&&(e[r]=c[r]);l&&l(t);while(m.length)m.shift()();return o.push.apply(o,u||[]),n()}function n(){for(var e,t=0;t<o.length;t++){for(var n=o[t],r=!0,s=1;s<n.length;s++){var c=n[s];0!==a[c]&&(r=!1)}r&&(o.splice(t--,1),e=i(i.s=n[0]))}return e}var r={},a={app:0},o=[];function i(t){if(r[t])return r[t].exports;var n=r[t]={i:t,l:!1,exports:{}};return e[t].call(n.exports,n,n.exports,i),n.l=!0,n.exports}i.m=e,i.c=r,i.d=function(e,t,n){i.o(e,t)||Object.defineProperty(e,t,{enumerable:!0,get:n})},i.r=function(e){"undefined"!==typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(e,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(e,"__esModule",{value:!0})},i.t=function(e,t){if(1&t&&(e=i(e)),8&t)return e;if(4&t&&"object"===typeof e&&e&&e.__esModule)return e;var n=Object.create(null);if(i.r(n),Object.defineProperty(n,"default",{enumerable:!0,value:e}),2&t&&"string"!=typeof e)for(var r in e)i.d(n,r,function(t){return e[t]}.bind(null,r));return n},i.n=function(e){var t=e&&e.__esModule?function(){return e["default"]}:function(){return e};return i.d(t,"a",t),t},i.o=function(e,t){return Object.prototype.hasOwnProperty.call(e,t)},i.p="/";var s=window["webpackJsonp"]=window["webpackJsonp"]||[],c=s.push.bind(s);s.push=t,s=s.slice();for(var u=0;u<s.length;u++)t(s[u]);var l=c;o.push([0,"chunk-vendors"]),n()})({0:function(e,t,n){e.exports=n("cd49")},"0017":function(e,t,n){"use strict";var r=n("08fb"),a=n.n(r);a.a},"034f":function(e,t,n){"use strict";var r=n("85ec"),a=n.n(r);a.a},"08fb":function(e,t,n){},"1eb9":function(e,t,n){},3321:function(e,t,n){"use strict";var r=n("5e49"),a=n.n(r);a.a},4678:function(e,t,n){var r={"./af":"2bfb","./af.js":"2bfb","./ar":"8e73","./ar-dz":"a356","./ar-dz.js":"a356","./ar-kw":"423e","./ar-kw.js":"423e","./ar-ly":"1cfd","./ar-ly.js":"1cfd","./ar-ma":"0a84","./ar-ma.js":"0a84","./ar-sa":"8230","./ar-sa.js":"8230","./ar-tn":"6d83","./ar-tn.js":"6d83","./ar.js":"8e73","./az":"485c","./az.js":"485c","./be":"1fc1","./be.js":"1fc1","./bg":"84aa","./bg.js":"84aa","./bm":"a7fa","./bm.js":"a7fa","./bn":"9043","./bn.js":"9043","./bo":"d26a","./bo.js":"d26a","./br":"6887","./br.js":"6887","./bs":"2554","./bs.js":"2554","./ca":"d716","./ca.js":"d716","./cs":"3c0d","./cs.js":"3c0d","./cv":"03ec","./cv.js":"03ec","./cy":"9797","./cy.js":"9797","./da":"0f14","./da.js":"0f14","./de":"b469","./de-at":"b3eb","./de-at.js":"b3eb","./de-ch":"bb71","./de-ch.js":"bb71","./de.js":"b469","./dv":"598a","./dv.js":"598a","./el":"8d47","./el.js":"8d47","./en-SG":"cdab","./en-SG.js":"cdab","./en-au":"0e6b","./en-au.js":"0e6b","./en-ca":"3886","./en-ca.js":"3886","./en-gb":"39a6","./en-gb.js":"39a6","./en-ie":"e1d3","./en-ie.js":"e1d3","./en-il":"7333","./en-il.js":"7333","./en-nz":"6f50","./en-nz.js":"6f50","./eo":"65db","./eo.js":"65db","./es":"898b","./es-do":"0a3c","./es-do.js":"0a3c","./es-us":"55c9","./es-us.js":"55c9","./es.js":"898b","./et":"ec18","./et.js":"ec18","./eu":"0ff2","./eu.js":"0ff2","./fa":"8df4","./fa.js":"8df4","./fi":"81e9","./fi.js":"81e9","./fo":"0721","./fo.js":"0721","./fr":"9f26","./fr-ca":"d9f8","./fr-ca.js":"d9f8","./fr-ch":"0e49","./fr-ch.js":"0e49","./fr.js":"9f26","./fy":"7118","./fy.js":"7118","./ga":"5120","./ga.js":"5120","./gd":"f6b4","./gd.js":"f6b4","./gl":"8840","./gl.js":"8840","./gom-latn":"0caa","./gom-latn.js":"0caa","./gu":"e0c5","./gu.js":"e0c5","./he":"c7aa","./he.js":"c7aa","./hi":"dc4d","./hi.js":"dc4d","./hr":"4ba9","./hr.js":"4ba9","./hu":"5b14","./hu.js":"5b14","./hy-am":"d6b6","./hy-am.js":"d6b6","./id":"5038","./id.js":"5038","./is":"0558","./is.js":"0558","./it":"6e98","./it-ch":"6f12","./it-ch.js":"6f12","./it.js":"6e98","./ja":"079e","./ja.js":"079e","./jv":"b540","./jv.js":"b540","./ka":"201b","./ka.js":"201b","./kk":"6d79","./kk.js":"6d79","./km":"e81d","./km.js":"e81d","./kn":"3e92","./kn.js":"3e92","./ko":"22f8","./ko.js":"22f8","./ku":"2421","./ku.js":"2421","./ky":"9609","./ky.js":"9609","./lb":"440c","./lb.js":"440c","./lo":"b29d","./lo.js":"b29d","./lt":"26f9","./lt.js":"26f9","./lv":"b97c","./lv.js":"b97c","./me":"293c","./me.js":"293c","./mi":"688b","./mi.js":"688b","./mk":"6909","./mk.js":"6909","./ml":"02fb","./ml.js":"02fb","./mn":"958b","./mn.js":"958b","./mr":"39bd","./mr.js":"39bd","./ms":"ebe4","./ms-my":"6403","./ms-my.js":"6403","./ms.js":"ebe4","./mt":"1b45","./mt.js":"1b45","./my":"8689","./my.js":"8689","./nb":"6ce3","./nb.js":"6ce3","./ne":"3a39","./ne.js":"3a39","./nl":"facd","./nl-be":"db29","./nl-be.js":"db29","./nl.js":"facd","./nn":"b84c","./nn.js":"b84c","./pa-in":"f3ff","./pa-in.js":"f3ff","./pl":"8d57","./pl.js":"8d57","./pt":"f260","./pt-br":"d2d4","./pt-br.js":"d2d4","./pt.js":"f260","./ro":"972c","./ro.js":"972c","./ru":"957c","./ru.js":"957c","./sd":"6784","./sd.js":"6784","./se":"ffff","./se.js":"ffff","./si":"eda5","./si.js":"eda5","./sk":"7be6","./sk.js":"7be6","./sl":"8155","./sl.js":"8155","./sq":"c8f3","./sq.js":"c8f3","./sr":"cf1e","./sr-cyrl":"13e9","./sr-cyrl.js":"13e9","./sr.js":"cf1e","./ss":"52bd","./ss.js":"52bd","./sv":"5fbd","./sv.js":"5fbd","./sw":"74dc","./sw.js":"74dc","./ta":"3de5","./ta.js":"3de5","./te":"5cbb","./te.js":"5cbb","./tet":"576c","./tet.js":"576c","./tg":"3b1b","./tg.js":"3b1b","./th":"10e8","./th.js":"10e8","./tl-ph":"0f38","./tl-ph.js":"0f38","./tlh":"cf75","./tlh.js":"cf75","./tr":"0e81","./tr.js":"0e81","./tzl":"cf51","./tzl.js":"cf51","./tzm":"c109","./tzm-latn":"b53d","./tzm-latn.js":"b53d","./tzm.js":"c109","./ug-cn":"6117","./ug-cn.js":"6117","./uk":"ada2","./uk.js":"ada2","./ur":"5294","./ur.js":"5294","./uz":"2e8c","./uz-latn":"010e","./uz-latn.js":"010e","./uz.js":"2e8c","./vi":"2921","./vi.js":"2921","./x-pseudo":"fd7e","./x-pseudo.js":"fd7e","./yo":"7f33","./yo.js":"7f33","./zh-cn":"5c3a","./zh-cn.js":"5c3a","./zh-hk":"49ab","./zh-hk.js":"49ab","./zh-tw":"90ea","./zh-tw.js":"90ea"};function a(e){var t=o(e);return n(t)}function o(e){if(!n.o(r,e)){var t=new Error("Cannot find module '"+e+"'");throw t.code="MODULE_NOT_FOUND",t}return r[e]}a.keys=function(){return Object.keys(r)},a.resolve=o,e.exports=a,a.id="4678"},"5e49":function(e,t,n){},"65d8":function(e,t,n){"use strict";var r=n("8712"),a=n.n(r);a.a},"85ec":function(e,t,n){},8712:function(e,t,n){},bc18:function(e,t,n){"use strict";var r=n("1eb9"),a=n.n(r);a.a},cd49:function(e,t,n){"use strict";n.r(t);n("e260"),n("e6cf"),n("cca6"),n("a79d");var r,a,o=n("2b0e"),i=function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{staticClass:"all_groups"},[n("div",{attrs:{id:"app"}},[n("MeGroupList")],1)])},s=[],c=n("d4ec"),u=n("99de"),l=n("7e84"),v=n("262e"),m=n("9ab4"),f=n("60a3"),d=(n("f9e3"),n("2dd8"),function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{staticClass:"cmo_source"},e._l(e.fileList,(function(e,t){return n("div",{key:t},[n("div",[n("div"),n("div",[n("MeEventGroupList",{attrs:{meGroupText:e.meGroup}})],1)])])})),0)}),p=[],b=n("bee2"),g=(n("99af"),n("d81d"),n("d3b7"),function(){function e(t){Object(c["a"])(this,e),this.catCamUrl=t}return Object(b["a"])(e,[{key:"getMovieList",value:function(e){var t="".concat(this.catCamUrl,"catcam/movies/{meGroup}");return console.log("getMovieList:url=".concat(t)),fetch(t).then((function(e){return e.json()}))}},{key:"formatMeEvent",value:function(e){var t=e;return t}},{key:"formatMovie",value:function(e){var t=e;return t}},{key:"formatMeGroup",value:function(e){var t=e;return t}},{key:"formatMeEventGroup",value:function(e){var t=e;return t}},{key:"getMeEventList",value:function(e,t,n,r,a){var o=this,i="".concat(this.catCamUrl,"catcam/meeventgroup/").concat(e,"/").concat(t,"?howMayRecords=").concat(n,"&startRecord=").concat(r,"&minimumDuration=").concat(a);return console.log("getMeEventList:url=".concat(i)),e||(e="dummp_arg"),t||(t="dummp_arg"),fetch(i).then((function(e){return e.json()})).then((function(e){return e.map((function(e){return o.formatMeEvent(e)}))}))}},{key:"getMeEventGroupList",value:function(e,t){var n=this,r="".concat(this.catCamUrl,"catcam/meeventgroup/").concat(e,"?howMayRecords=").concat(t);return console.log("getMeEventGroupList:url=".concat(r)),fetch(r).then((function(e){return e.json()})).then((function(e){return e.map((function(e){return n.formatMeGroup(e)}))}))}},{key:"getMeGroupList",value:function(){var e=this,t="".concat(this.catCamUrl,"catcam/megroups");return console.log("getMeGroupList:url=".concat(t)),fetch(t).then((function(e){return e.json()})).then((function(t){return t.map((function(t){return e.formatMeGroup(t)}))}))}},{key:"getMovieListX",value:function(){var e=this,t="".concat(this.catCamUrl,"catcam/movies");return console.log("getMovieList:url=".concat(t)),fetch(t).then((function(e){return e.json()})).then((function(t){return t.map((function(t){return e.formatMovie(t)}))}))}}]),e}()),h=function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{staticClass:"cmo_event_group"},[n("h2",[e._v(e._s(e.meGroupText)+" "),n("select",{directives:[{name:"model",rawName:"v-model",value:e.selectedMeEventGroupText,expression:"selectedMeEventGroupText"}],on:{change:function(t){var n=Array.prototype.filter.call(t.target.options,(function(e){return e.selected})).map((function(e){var t="_value"in e?e._value:e.value;return t}));e.selectedMeEventGroupText=t.target.multiple?n:n[0]}}},e._l(e.fileList,(function(t,r){return n("option",{key:r},[e._v(e._s(t.meEventGroup))])})),0)]),e.getSelectedMeEventGroup()?n("div",[n("MeEventList",{key:e.selectedMeEventGroupText,attrs:{meGroupText:e.meGroupText,meEventGroupText:e.getSelectedMeEventGroup()}})],1):n("div",[e._v("No event group selected")])])},j=[],_=function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{staticClass:"cmo_div_outer"},[e.isDialogActive()?n("div",[n("div",{staticClass:"cmo img movie_box_center"},[n("MeEventDetail",{attrs:{meEvent:e.getCurrentMovie()}}),n("center",[n("button",{on:{click:function(t){return e.closeDialog()}}},[e._v("close")])])],1)]):n("div"),n("div",[e._l(e.fileList,(function(t,r){return n("div",{key:r,staticClass:"cmo_div_wrapper"},[n("div",{staticClass:"cmo_div",on:{click:function(n){return e.watchMovie(t)}}},[n("div",[n("b",[e._v(e._s(e.getTimePassedToMe(t,r))+" ago at "+e._s(e.getDateTime(t))),n("br"),e._v(" "+e._s(t.me_time.toFixed(1))+" seconds (click to watch video)")])]),n("div",[n("img",{staticClass:"cmo_img",attrs:{src:e.getRepImageSrc(t)}})]),n("div",[n("b",[e._v(e._s(e.getTime2PreviousMe(t,r)))])])])])})),n("br"),n("center",[n("button",{on:{click:function(t){return e.getMore()}}},[e._v("...more...")])])],2)])},y=[],M=(n("a4d3"),n("e01a"),n("d28b"),n("b680"),n("3ca3"),n("ddb0"),function(){function e(t){Object(c["a"])(this,e),this.me_group=t.me_group,this.me_event_group=t.me_event_group,this.me_name=t.me_name,this.me_tag=t.me_tag,this.me_image_array=t.me_image_array,this.me_delta_array=t.me_delta_array,this.me_time=t.me_time,this.me_video_name=t.me_video_name,this.me_rep_image=t.me_rep_image,this.me_json_name=t.me_json_name}return Object(b["a"])(e,[{key:"getFirstMeEventTime",value:function(){var e="";return this.me_delta_array.length>0&&(e=this.me_delta_array[0].event_time_iso),e}},{key:"getLastMeEventTime",value:function(){var e="";return this.me_delta_array.length>0&&(e=this.me_delta_array[this.me_delta_array.length-1].event_time_iso),e}}],[{key:"getNewEmptyMeEvent",value:function(){var t=new e({me_group:"",me_event_group:"",me_name:"",me_tag:"",me_image_array:[],me_delta_array:[],me_time:-1,me_video_name:"",me_rep_image:"",me_json_name:""});return t}},{key:"getNewFromMeEvent",value:function(t){var n=new e(t);return n}},{key:"getDateFullFromMeEvent",value:function(e){var t=this.getDateYear(e)+" "+this.getDateTime(e);return t}},{key:"getDateTime",value:function(e){var t="".concat(e.me_name.substring(9,11),":").concat(e.me_name.substring(11,13),".").concat(e.me_name.substring(13,15));return t}},{key:"getDateYear",value:function(e){var t="".concat(e.me_name.substring(0,4),"-").concat(e.me_name.substring(4,6),"-").concat(e.me_name.substring(6,8));return t}}]),e}()),E=function(){function e(){Object(c["a"])(this,e)}return Object(b["a"])(e,[{key:"getDateFullFromMeEvent",value:function(e){var t=this.getDateYear(e)+" "+this.getDateTime(e);return t}},{key:"getDateTime",value:function(e){var t="".concat(e.me_name.substring(9,11),":").concat(e.me_name.substring(11,13),".").concat(e.me_name.substring(13,15));return t}},{key:"getDateYear",value:function(e){var t="".concat(e.me_name.substring(0,4),"-").concat(e.me_name.substring(4,6),"-").concat(e.me_name.substring(6,8));return t}}]),e}(),k=function(){var e=this,t=e.$createElement,n=e._self._c||t;return n("div",{staticClass:"cmo_img me_event_detail"},[n("div",[e._v(e._s(e.getDateFromMeEvent(e.meEvent))+" -- "+e._s(e.meEvent.me_time.toFixed(1))+" seconds"),n("br"),n("video",{staticClass:"cmo_img",attrs:{controls:"",src:e.getMovieSrc(),type:"video/mp4"}},[e._v(" Your browser does not support the video tag. ")]),n("div",{staticClass:"cmo_wrap"},[n("MeEventDetailGraph",{attrs:{width:600,height:100,data:e.getChartData(e.meEvent),options:e.getChartOptions(e.meEvent)}})],1),n("div",[e._v(" "+e._s(e.meEvent.me_delta_array.length)+" frames fps="+e._s((e.meEvent.me_delta_array.length/e.meEvent.me_time).toFixed(0))+" ")])])])},w=[],O=n("1fca"),T={extends:O["a"],props:["data","options"],mounted:function(){this.renderChart(this.data,this.options)}},G=T,x=n("2877"),L=Object(x["a"])(G,r,a,!1,null,null,null),D=L.exports,C=function(e){function t(){return Object(c["a"])(this,t),Object(u["a"])(this,Object(l["a"])(t).apply(this,arguments))}return Object(v["a"])(t,e),Object(b["a"])(t,[{key:"getChartData",value:function(e){var t=new Array,n=new Array,r=new Array,a=0,o=0,i=!0,s=!1,c=void 0;try{for(var u,l=e.me_delta_array[Symbol.iterator]();!(i=(u=l.next()).done);i=!0){var v=u.value;v.contours.length>o&&(o=v.contours.length);var m=0,f=!0,d=!1,p=void 0;try{for(var b,g=v.contours[Symbol.iterator]();!(f=(b=g.next()).done);f=!0){var h=b.value;m+=h.h*h.h}}catch(A){d=!0,p=A}finally{try{f||null==g.return||g.return()}finally{if(d)throw p}}m>a&&(a=m)}}catch(A){s=!0,c=A}finally{try{i||null==l.return||l.return()}finally{if(s)throw c}}var j=!0,_=!1,y=void 0;try{for(var M,E=e.me_delta_array[Symbol.iterator]();!(j=(M=E.next()).done);j=!0){var k=M.value,w=Date.parse(e.me_delta_array[0].event_time_iso),O=Date.parse(k.event_time_iso),T=O-w;T=Math.round(T/1e3),t.push(T+"");var G=0,x=!0,L=!1,D=void 0;try{for(var C,S=k.contours[Symbol.iterator]();!(x=(C=S.next()).done);x=!0){var F=C.value;G+=F.h*F.h}}catch(A){L=!0,D=A}finally{try{x||null==S.return||S.return()}finally{if(L)throw D}}r.push(100*G/a),n.push(100*k.contours.length/(1*o))}}catch(A){_=!0,y=A}finally{try{j||null==E.return||E.return()}finally{if(_)throw y}}var z={labels:t,datasets:[{label:"movement area",backgroundColor:"#f87979",pointBackgroundColor:"white",borderWidth:5,pointBorderColor:"#249EBF",data:r,pointRadius:0,fill:!1,borderColor:"#249EBF"},{label:"# areas",backgroundColor:"#7e7eff",pointBackgroundColor:"000000",borderWidth:5,pointBorderColor:"#000000",data:n,pointRadius:0,fill:!1,borderColor:"#7e7eff"}]};return z}},{key:"getChartOptions",value:function(e){return{scales:{yAxes:[{ticks:{beginAtZero:!0},gridLines:{display:!1}}],xAxes:[{gridLines:{display:!1}}]},legend:{display:!1},responsive:!0,maintainAspectRatio:!1}}},{key:"getDateFromMeEvent",value:function(e){return(new E).getDateFullFromMeEvent(e)}},{key:"meEventChanged",value:function(e){console.log("meEventGroupTextChanged!!v newVal=".concat(e.me_name))}},{key:"getMovieSrc",value:function(){var e=this.meEvent,t="http://192.168.1.177:9090/catcam/data/".concat(e.me_group,"/").concat(e.me_event_group,"/").concat(e.me_name,"/").concat(e.me_video_name);return t}}]),t}(f["c"]);Object(m["a"])([Object(f["b"])()],C.prototype,"meEvent",void 0),Object(m["a"])([Object(f["d"])("meEvent",{immediate:!0,deep:!0})],C.prototype,"meEventChanged",null),C=Object(m["a"])([Object(f["a"])({components:{MeEventDetailGraph:D}})],C);var S=C,F=S,z=(n("bc18"),Object(x["a"])(F,k,w,!1,null,"63773450",null)),A=z.exports,R=function(e){function t(){var e;return Object(c["a"])(this,t),e=Object(u["a"])(this,Object(l["a"])(t).apply(this,arguments)),e.fileList=e.geMeEventList(),e.howMayRecords=8,e.startRecord=0,e.minimumDuration=6,e.dialogActive=!1,e.currentMeEvent=M.getNewEmptyMeEvent(),e}return Object(v["a"])(t,e),Object(b["a"])(t,[{key:"getTimePassedToMe",value:function(e,t){var n=new M(e),r=0;if(t>0){var a=new Date(Date.now()),o=(new M(this.fileList[t-1]),new Date(Date.parse(n.getLastMeEventTime())));r=a.getTime()-o.getTime()}else{var i=new Date(Date.now()),s=new Date(Date.parse(n.getLastMeEventTime()));r=i.getTime()-s.getTime()}var c=this.msToStringAgo(r);return c}},{key:"getTime2PreviousMe",value:function(e,t){var n=new M(e),r=0;if(t<this.fileList.length-1){var a=new Date(Date.now()),o=new M(this.fileList[t+1]);a.setTime(Date.parse(o.getLastMeEventTime()));var i=new Date(Date.parse(n.getLastMeEventTime()));return r=i.getTime()-a.getTime(),"previous event ".concat(this.msToStringAgo(r))}var s=new Date(Date.now()),c=new Date(Date.parse(n.getLastMeEventTime()));return r=s.getTime()-c.getTime(),"last event"}},{key:"getTime2NextMe",value:function(e,t){var n=new M(e),r=0;if(t>0){var a=new Date(Date.now()),o=new M(this.fileList[t-1]);a.setTime(Date.parse(o.getLastMeEventTime()));var i=new Date(Date.parse(n.getLastMeEventTime()));return r=a.getTime()-i.getTime(),"next future event in ".concat(this.msToStringAgo(r))}var s=new Date(Date.now()),c=new Date(Date.parse(n.getLastMeEventTime()));return r=s.getTime()-c.getTime(),"most recent event"}},{key:"msToStringAgo",value:function(e){var t="",n=e/1e3,r=n/60,a=r/60,o=a/24;return t=n<60?"".concat(Math.round(n)," seconds"):r<10?"".concat(r.toFixed(1)," minutes"):r<60?"".concat(r.toFixed(0)," minutes"):a<4?"".concat(a.toFixed(1)," hours"):a<24?"".concat(a.toFixed(0)," hours"):"".concat(o<4?o.toFixed(1):o.toFixed(0)," days"),t}},{key:"getDateFromMeEvent",value:function(e){return(new E).getDateFullFromMeEvent(e)}},{key:"getDateTime",value:function(e){return(new E).getDateTime(e)}},{key:"isDialogActive",value:function(){return this.dialogActive}},{key:"closeDialog",value:function(){this.dialogActive=!1}},{key:"getCurrentMovie",value:function(){return this.currentMeEvent}},{key:"watchMovie",value:function(e){this.currentMeEvent=M.getNewFromMeEvent(e),this.dialogActive=!0}},{key:"meEventGroupTextChanged",value:function(e){console.log("meEventGroupText:Changed!!v newVal=".concat(e)),this.fileList=this.geMeEventList()}},{key:"getRepImageSrc",value:function(e){var t="http://192.168.1.177:9090/catcam/data/".concat(e.me_group,"/").concat(e.me_event_group,"/").concat(e.me_name,"/").concat(e.me_rep_image);return t}},{key:"getMovieHtml",value:function(e){return'<video controls :src="'.concat(this.getMovieSrc(e),'" type="video/mp4">Your browser does not support the video tag.</video>')}},{key:"getMovieSrc",value:function(e){var t="http://192.168.1.177:9090/catcam/data/".concat(e.me_group,"/").concat(e.me_event_group,"/").concat(e.me_name,"/").concat(e.me_video_name);return t}},{key:"changeinside",value:function(e){console.log("woooo! got the emit")}},{key:"getMore",value:function(){console.log("moooooooooooooooore"),this.startRecord+=this.howMayRecords,this.fileList=this.geMeEventList()}},{key:"geMeEventList",value:function(){var e=this,t=e.fileList?e.fileList:new Array;return console.log("geMeEventGroupList meGroup=".concat(this.meGroupText," meGroupEvent=").concat(this.meEventGroupText)),new g("http://192.168.1.177:9090/").getMeEventList(this.meGroupText,this.meEventGroupText,this.howMayRecords,this.startRecord?this.startRecord:0,this.minimumDuration).then((function(t){var n=!0,r=!1,a=void 0;try{for(var o,i=t[Symbol.iterator]();!(n=(o=i.next()).done);n=!0){var s=o.value;e.fileList.push(s)}}catch(c){r=!0,a=c}finally{try{n||null==i.return||i.return()}finally{if(r)throw a}}})),t}}]),t}(f["c"]);Object(m["a"])([Object(f["b"])()],R.prototype,"meGroupText",void 0),Object(m["a"])([Object(f["b"])()],R.prototype,"meEventGroupText",void 0),Object(m["a"])([Object(f["b"])({default:8})],R.prototype,"howMayRecords",void 0),Object(m["a"])([Object(f["b"])({default:6})],R.prototype,"minimumDuration",void 0),Object(m["a"])([Object(f["d"])("meEventGroupText",{immediate:!0,deep:!0})],R.prototype,"meEventGroupTextChanged",null),R=Object(m["a"])([Object(f["a"])({components:{MeEventDetail:A}})],R);var N=R,P=N,U=(n("3321"),Object(x["a"])(P,_,y,!1,null,"5bba3b76",null)),B=U.exports,Y=function(e){function t(){var e;return Object(c["a"])(this,t),e=Object(u["a"])(this,Object(l["a"])(t).apply(this,arguments)),e.selectedMeEventGroupText="",e.meEventGroup={meEventGroup:""},e.fileList=e.geMeEventGroupList(),e.howManyRecords=24,e}return Object(v["a"])(t,e),Object(b["a"])(t,[{key:"getSelectedMeEventGroup",value:function(){return console.log("getSelectedMeEventGroup:START numRecords=".concat(this.fileList.length," selectedMeEventGroupText=").concat(this.selectedMeEventGroupText)),this.selectedMeEventGroupText}},{key:"changeMgEventGroup",value:function(e){var t=e.target.options.selectedIndex;t>-1&&this.fileList&&(this.meEventGroup=this.fileList[t])}},{key:"getUtils",value:function(){return new E}},{key:"geMeEventGroupList",value:function(){var e=this;return console.log("geMeEventGroupList meGroup=".concat(this.meGroupText)),this.meGroupText&&new g("http://192.168.1.177:9090/").getMeEventGroupList(this.meGroupText,this.howManyRecords).then((function(t){e.fileList=t,e.fileList.length>0&&(e.meEventGroup=e.fileList[0]),e.selectedMeEventGroupText=e.meEventGroup.meEventGroup,console.log("geMeEventGroupList="+JSON.stringify(t))})),new Array}}]),t}(f["c"]);Object(m["a"])([Object(f["b"])()],Y.prototype,"meGroupText",void 0),Object(m["a"])([Object(f["b"])({default:24})],Y.prototype,"howManyRecords",void 0),Y=Object(m["a"])([Object(f["a"])({components:{MeEventList:B}})],Y);var $=Y,J=$,I=(n("65d8"),Object(x["a"])(J,h,j,!1,null,"4e56de92",null)),q=I.exports,V=function(e){function t(){var e;return Object(c["a"])(this,t),e=Object(u["a"])(this,Object(l["a"])(t).apply(this,arguments)),e.fileList=e.geMeGroupList(),e}return Object(v["a"])(t,e),Object(b["a"])(t,[{key:"openMovie",value:function(e){}},{key:"geMeGroupList",value:function(){var e=this;return new g("http://192.168.1.177:9090/").getMeGroupList().then((function(t){e.fileList=t,console.log("geMeGroupList="+JSON.stringify(t))})),new Array}}]),t}(f["c"]);V=Object(m["a"])([Object(f["a"])({components:{MeEventGroupList:q}})],V);var W=V,H=W,X=(n("0017"),Object(x["a"])(H,d,p,!1,null,"b6b2b3c8",null)),Z=X.exports,K=function(e){function t(){return Object(c["a"])(this,t),Object(u["a"])(this,Object(l["a"])(t).apply(this,arguments))}return Object(v["a"])(t,e),t}(f["c"]);K=Object(m["a"])([Object(f["a"])({components:{MeGroupList:Z,MeEventGroupList:q}})],K);var Q=K,ee=Q,te=(n("034f"),Object(x["a"])(ee,i,s,!1,null,null,null)),ne=te.exports;new o["a"]({render:function(e){return e(ne)}}).$mount("#app")}});
//# sourceMappingURL=app.93ebcc4c.js.map