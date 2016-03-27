Radiobeacon - Openbmap Unified Network Location Provider
Copyright (C) 2013  wish7

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Openbmap Unified Network Location Provider is a module for offline geolocation with the UnifiedNLP framework.
UnifiedNLP (https://github.com/microg/android_packages_apps_UnifiedNlp) is a great framework, which intends to replace Google's propriertary geolocations services.
Thereby UnifiedNLP is a pure backend framework, meaning it doesn't provide any geolocation mechanisms by itself.
Instead you choose a concrete module which does the actual geolocation part.

The Openbmap geolocation module is one such module, but there's also a variety of other open source modules
(e.g. opencellid or Mozilla location service)

You may operate this module completely OFFLINE, if you download a copy of our geolocation database (check 'Settings'!).
Per default this module operates in ONLINE mode.

In latter case this module sends visible cells and wifi bssids to our server, which in turn provides the estimated location.
We promise not save any of this uploaded information, but if you're as privacy-aware as we are, you should choose the offline mode.
By using offline mode you're doing our server a great favour anyways :-)

History

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