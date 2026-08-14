> Migrated from `IMAC-ORG/imac-judge-app`#<!---->77 — originally opened by @<!---->DevDavidW on 2025-04-27T15:00:14Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/77

Add option when publishing reports to also upload to the aero-judge.com site (when internet is available)


---

@<!---->dpgarceau commented on 2025-11-22T19:47:05Z:

I've about got the aero-judge website side of things setup to handle this and be able to display concurrent live events automatically, with all the other archival tasks automated to keep it flowing....

I'm asking about Score capabilities as I haven't got access to score to be able to look ( @<!---->DanCInOz @<!---->sputnik-dancarroll - it would be nice to have access to that repo)

How It Works

  1. Score! exports a zip file containing:
    - report_results3.xml (required - contains event data)
    - report_results.html (main results page)
    - report_pilot_{*}.html (individual pilot detail pages)
  2. Score! POSTs the zip to our API endpoint
  3. Our server automatically:
    - Extracts the HTML files
    - Parses metadata from XML (event name, date, region, classes, participant count)
    - Stores metadata in database
    - Deploys HTML files to results/live/{event_id}/
    - Results are immediately viewable at https://aero-judge.com/results/live/{event_id}/report_results.html

  API Endpoint (Phase 1 - Coming Soon)

  POST https://aero-judge.com/api/results/upload        
  Headers:
    X-API-Key: {provided_api_key}
    Content-Type: multipart/form-data
  Body:
    file: {zip file}

  What Score! Needs to Add

  1. Add event ID into XML - Add <event_id> tag to report_results3.xml:
 ''' <report>
      <type>results</type>
      <event_id>2503</event_id>  **<!-- NEW: Unique event identifier - This exists but is not in the xml file -->**
      <contest_name>2025 SE Season Opener</contest_name>
      <timestamp>8-Mar-2025 20:16</timestamp>
      <region>SE</region>
      ...
  </report>'''
  2. HTTP POST capability - Ability to upload the zip to our endpoint with API key authentication       


  **Questions for Score! Dev**

  1. Can Score! make HTTP POST requests with file       
  uploads?

  2. What can we use event id and add it to report_results3.xml 

  3. Can we add a checkbox on the reports page that sends the zip if checked (and online).

---

@<!---->dpgarceau commented on 2026-07-21T16:31:51Z:

built a companion app to handle this that is distributed to CD with their score packages. Will ultimately be written into the score replacement software. 

