const { chromium } = require(process.env.PLAYWRIGHT_MODULE);
(async () => {
 const browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH, headless: true });
 const page = await browser.newPage({viewport:{width:1440,height:1000}});
 page.on('pageerror', error => console.log('PAGE ERROR', error.message));
 await page.addInitScript(() => {
   window.audioPeak=0;
   const original=AudioNode.prototype.connect;
   AudioNode.prototype.connect=function(...args){
     const result=original.apply(this,args);
     if(args[0] instanceof AudioDestinationNode && !window.audioAnalyser){
       const analyser=this.context.createAnalyser(); original.call(this,analyser);
       window.audioAnalyser=analyser;
       setInterval(()=>{const data=new Float32Array(analyser.fftSize);analyser.getFloatTimeDomainData(data);for(const v of data)window.audioPeak=Math.max(window.audioPeak,Math.abs(v));},20);
     }
     return result;
   };
 });
 await page.goto(process.env.SCORE_URL || 'http://localhost:18937/web/');
 await page.waitForFunction(() => window.xstarScoreRuntime?.state.api?.isReadyForPlayback, {timeout:20000});
 await page.click('#play-button');
 await page.waitForTimeout(3000);
 console.log('playback',await page.evaluate(()=>({ready:window.xstarScoreRuntime.state.api.isReadyForPlayback,tick:window.xstarScoreRuntime.state.api.tickPosition,peak:window.audioPeak,status:document.querySelector('#position-status').textContent,scroll:window.scrollY,cursor:!!document.querySelector('.playback-cursor')})));
 await page.evaluate(()=>{window.xstarScoreRuntime.state.api.tickPosition=40*3840;});
 await page.waitForTimeout(1500);
 console.log('seek',await page.evaluate(()=>({status:document.querySelector('#position-status').textContent,scroll:window.scrollY,peak:window.audioPeak})));
 await browser.close();
})();
