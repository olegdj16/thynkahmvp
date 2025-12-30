    function updateOfflineBanner() {
    const banner = document.getElementById("offlineBanner");
    if (!banner) return;

    if (!navigator.onLine) {
    banner.style.display = "block";
} else {
    banner.style.display = "none";
}
}

    window.addEventListener("offline", updateOfflineBanner);
    window.addEventListener("online", () => {
    updateOfflineBanner();
    if (typeof showToast === "function") {
    showToast("Back online");
}
});

    updateOfflineBanner(); // initial check