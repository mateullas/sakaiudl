function selectContentFromIdiom() {
    const lang = document.documentElement.getAttribute('lang');
	console.log(lang);
	const contenidorsIframe = document.getElementsByClassName('Mrphs-toolBody Mrphs-toolBody--sakai-motd');
	console.log(contenidorsIframe);
    if (contenidorsIframe.length > 0) {
        // si el trobem vol dir que son dins iframe
        let frameObj = contenidorsIframe[0].firstElementChild;
		let contenidorsMOTD_Iframe = frameObj.contentWindow.document.getElementsByClassName('contingutsMOTD');
		console.log(contenidorsMOTD_Iframe);	
        if (contenidorsMOTD_Iframe.length > 0) {
            for (var i = 0; i < contenidorsMOTD_Iframe.length; i++) {
				if (contenidorsMOTD_Iframe[i].id === lang) {
					console.log("Mostrem continguts iframe "+contenidorsMOTD_Iframe[i].id);
					contenidorsMOTD_Iframe[i].style.display="block";
        
                }
				else {
		            contenidorsMOTD_Iframe[i].style.display="none";
    			}
            }
        }
        else {
            console("Sense continguts MOTD dins iframe");
        } 	
    }
    else {
        // no son dins iframe els hem de trobar directament
        const contenidorsMOTD = document.getElementsByClassName('contingutsMOTD');
        console.log(contenidorsMOTD);
        if (contenidorsMOTD.length > 0) {
            for (var i = 0; i < contenidorsMOTD.length; i++) {
				if (contenidorsMOTD[i].id === lang) {
					console.log("Mostrem continguts "+contenidorsMOTD[i].id);
					contenidorsMOTD[i].style.display="block";
        
                }
				else {
		            contenidorsMOTD[i].style.display="none";
    			}
            }
        }
    }
}
window.onload = function() {
	selectContentFromIdiom();
};