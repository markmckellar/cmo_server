(function(t){function e(e){for(var r,a,c=e[0],u=e[1],s=e[2],f=0,v=[];f<c.length;f++)a=c[f],Object.prototype.hasOwnProperty.call(o,a)&&o[a]&&v.push(o[a][0]),o[a]=0;for(r in u)Object.prototype.hasOwnProperty.call(u,r)&&(t[r]=u[r]);l&&l(e);while(v.length)v.shift()();return i.push.apply(i,s||[]),n()}function n(){for(var t,e=0;e<i.length;e++){for(var n=i[e],r=!0,c=1;c<n.length;c++){var u=n[c];0!==o[u]&&(r=!1)}r&&(i.splice(e--,1),t=a(a.s=n[0]))}return t}var r={},o={app:0},i=[];function a(e){if(r[e])return r[e].exports;var n=r[e]={i:e,l:!1,exports:{}};return t[e].call(n.exports,n,n.exports,a),n.l=!0,n.exports}a.m=t,a.c=r,a.d=function(t,e,n){a.o(t,e)||Object.defineProperty(t,e,{enumerable:!0,get:n})},a.r=function(t){"undefined"!==typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(t,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(t,"__esModule",{value:!0})},a.t=function(t,e){if(1&e&&(t=a(t)),8&e)return t;if(4&e&&"object"===typeof t&&t&&t.__esModule)return t;var n=Object.create(null);if(a.r(n),Object.defineProperty(n,"default",{enumerable:!0,value:t}),2&e&&"string"!=typeof t)for(var r in t)a.d(n,r,function(e){return t[e]}.bind(null,r));return n},a.n=function(t){var e=t&&t.__esModule?function(){return t["default"]}:function(){return t};return a.d(e,"a",e),e},a.o=function(t,e){return Object.prototype.hasOwnProperty.call(t,e)},a.p="/";var c=window["webpackJsonp"]=window["webpackJsonp"]||[],u=c.push.bind(c);c.push=e,c=c.slice();for(var s=0;s<c.length;s++)e(c[s]);var l=u;i.push([0,"chunk-vendors"]),n()})({0:function(t,e,n){t.exports=n("cd49")},"034f":function(t,e,n){"use strict";var r=n("85ec"),o=n.n(r);o.a},"5fc0":function(t,e,n){},"684b":function(t,e,n){"use strict";var r=n("5fc0"),o=n.n(r);o.a},"85ec":function(t,e,n){},cd49:function(t,e,n){"use strict";n.r(e);n("e260"),n("e6cf"),n("cca6"),n("a79d");var r=n("2b0e"),o=function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{attrs:{id:"app"}},[n("CatCamVideoList")],1)},i=[],a=n("d4ec"),c=n("99de"),u=n("7e84"),s=n("262e"),l=n("9ab4"),f=n("60a3"),v=(n("f9e3"),n("2dd8"),function(){var t=this,e=t.$createElement,n=t._self._c||e;return n("div",{staticClass:"movies"},[n("h1",[t._v("Motion events : ")]),n("div",t._l(t.fileList,(function(e,r){return n("div",{key:r,staticClass:"movie_button"},[n("hr"),t._v(t._s(e.movieName)+" : "+t._s(e.eventData.meData.me_time)+" seconds"),n("br"),n("video",{staticClass:"tab",attrs:{controls:""}},[t._v("Your browser does not support the <video> tag."),n("source",{attrs:{src:e.movieUrl}})])])})),0)])}),p=[],b=n("bee2"),d=(n("d81d"),n("d3b7"),function(){function t(e){Object(a["a"])(this,t),this.catCamUrl=e}return Object(b["a"])(t,[{key:"getMovieList",value:function(){var t="".concat(this.catCamUrl,"catcam/movies");return console.log("getMovieList:url=".concat(t)),fetch(t).then((function(t){return t.json()}))}},{key:"formatMovie",value:function(t){var e=t;return e}},{key:"getMovieListX",value:function(){var t=this,e="".concat(this.catCamUrl,"catcam/movies");return console.log("getMovieList:url=".concat(e)),fetch(e).then((function(t){return t.json()})).then((function(e){return e.map((function(e){return t.formatMovie(e)}))}))}}]),t}()),h=function(t){function e(){var t;return Object(a["a"])(this,e),t=Object(c["a"])(this,Object(u["a"])(e).apply(this,arguments)),t.fileList=t.geMovietList(),t}return Object(s["a"])(e,t),Object(b["a"])(e,[{key:"openMovie",value:function(t){alert("url="+t.movieUrl)}},{key:"geMovietList",value:function(){var t=this;return new d("http://192.168.1.177:9090/").getMovieListX().then((function(e){t.fileList=e,console.log("movies="+JSON.stringify(e))})),new Array}}]),e}(f["b"]);h=Object(l["a"])([f["a"]],h);var m=h,O=m,j=(n("684b"),n("2877")),y=Object(j["a"])(O,v,p,!1,null,"04903344",null),g=y.exports,_=function(t){function e(){return Object(a["a"])(this,e),Object(c["a"])(this,Object(u["a"])(e).apply(this,arguments))}return Object(s["a"])(e,t),e}(f["b"]);_=Object(l["a"])([Object(f["a"])({components:{CatCamVideoList:g}})],_);var M=_,w=M,L=(n("034f"),Object(j["a"])(w,o,i,!1,null,null,null)),C=L.exports;new r["a"]({render:function(t){return t(C)}}).$mount("#app")}});
//# sourceMappingURL=app.c2da6cb9.js.map