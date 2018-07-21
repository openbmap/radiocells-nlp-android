Radiocells.org Unified Network Location Provider is a geolocation module for the UnifiedNLP framework.
License: AGPLv3

UnifiedNLP (https://github.com/microg/android_packages_apps_UnifiedNlp) is a great framework, which intends to replace Google's propriertary geolocations services. This provider is also compatible with the MicroG framework (https://microg.org/). UnifiedNLP provides the backend framework, but no actual geolocation mechanisms by itself. At this point the Radiocells.org geolocation module kicks in, but there's also a variety of other open source geolocation modules (e.g. Mozilla location service)

The Radiocells.org geolocation works either completly offline or online. For OFFLINE operation you need to download a copy of our geolocation database upfront (see 'Settings'!). Default setting is ONLINE mode. In this case a https request with currently visible cells and wifi bssids to our server to determine estimated location.

Privacy notice: we might use ONLINE queries to improve the radiocells.org database. In particular we might use the query data to add geolocation for yet unknown wifis by combining wifi and cell query data (correlation data). Besides that we don't collect any personal identifiable data information (no device ids, no fingerprinting, no adware). If you're really privacy concerned, please use OFFLINE mode only! Thanks.

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/org.openbmap/)

History

0.2.9
   - added Android 7+ support (new permission system)
   - fixed issues in ONLINE geolocation
   
0.2.2
   - major improvements on offline geolocation (thx mvglasow !!!! https://github.com/wish7code/org.openbmap.unifiedNlpProvider/pull/19)
   - switched to https completey
   
0.1.5
   - added support for cell-only geolocation (beta)
   
0.1.4
   - moved data folder to default Android location (thanks @ agilob)
   - added support for offline cells geolocation (beta)
     Be sure to update the offline database in settings!!!!!

0.1.3
    - Ignore _nomap wifis
    - added version info
0.1.2
    - Added folder selection dialog
    - Show last update date of local wifi catalog in settings