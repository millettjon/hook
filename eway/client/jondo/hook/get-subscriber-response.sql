-- This sql file is shared between the following:
-- webhook for site 11214 (cute kid) which uses text17
-- webhook for site 11214 (canvas people) which uses text18

select sr.subscriberresponseid, sr.respondingtoid, s.email, sa.text17, sa.text18,
(case when s.isactive = 'l' then 'blacklisted'
when  s.isactive = 'n' and s.bouncecount = 99 then 'hardbounce'
when s.isactive = 'n' then 'inactive'
when s.isactive = 'y' and s.creationdate - sr.creationdate between 0 and 60/86400 then 'new'
else 'exists' end) status,
s.isactive, s.bouncecount,
case when s.creationdate - sr.creationdate between 0 and 60/86400 then 'new' else 'exists' end db_status,
case when se.creationdate - sr.creationdate between 0 and 60/86400 then 'new' else 'exists' end list_status,
s.creationdate sub_date, sr.creationdate sr_date, se.creationdate se_date
from subscriberresponse sr, pagelink pl, offer o, subscriber_elist se, subscriber s, subscriberattribute sa
where o.siteid = :site_id
and o.responsetypecode = 'G'
and pl.offerid = o.offerid
and o.toelistid = se.elistid
and sr.respondingtoid = pl.pagelinkid
and sr.subscriberresponseid > :subscriber_response_id
and sr.subscriberid = se.subscriberid
and sr.subscriberid = s.subscriberid
and s.subscriberid = sa.subscriberid
and s.siteid = sa.siteid
order by sr.subscriberresponseid;
