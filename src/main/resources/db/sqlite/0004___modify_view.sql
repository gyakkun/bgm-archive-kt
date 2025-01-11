drop view if exists ba_v_topic_username_rank_by_last_reply_and_dateline;
drop view if exists ba_v_post_username_rank_by_reply_asc;

create view if not exists ba_v_topic_username_rank_by_last_reply_and_dateline as
select bt.*,
       bp.dateline as                                                             last_update_time,
       bu.username as                                                             username,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_last_reply,
       rank() over (partition by bt.type,bt.uid order by bp.dateline desc, bp.id desc) rank_dateline
from ba_topic bt
         inner join ba_post bp on bt.last_post_pid = bp.id and bt.type = bp.type
         inner join ba_user bu on bu.id = bt.uid;

create view if not exists ba_v_post_username_rank_by_reply_asc as
select bp.*,
       bt.title,
       bu.username as                                                                    username,
       rank() over (partition by bp.type,bp.mid, bp.uid order by bp.dateline desc,bp.id desc) rank_reply_asc
from ba_post bp
         inner join ba_topic bt on bp.type = bt.type and bt.id = bp.mid
         inner join ba_user bu on bu.id = bp.uid;