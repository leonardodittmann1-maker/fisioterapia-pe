chrome.action.onClicked.addListener(async tab => {
  if (!tab.id) return;
  if (!tab.url || new URL(tab.url).origin !== "https://photos.google.com") {
    await chrome.tabs.create({ url: "https://photos.google.com/" });
    return;
  }
  await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["review.js"] });
});
