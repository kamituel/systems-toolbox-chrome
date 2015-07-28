console.log('Background page running!');

chrome.runtime.onMessage.addListener(function (req, _sender, _sendResponse) {
  var tabId = req.tabId;
  var script = req.scriptToInject;

  console.log('Executing ', script, ' in tab ', tabId);
  chrome.tabs.executeScript(tabId, {file: script});
});
