package moe.nyamori.bgm.db

import moe.nyamori.bgm.model.Like
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.model.User
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transactional

interface BgmDao : Transactional<BgmDao> {

    @SqlQuery(
        """
        select 1
    """
    )
    fun healthCheck(): Int

    @SqlQuery(
        """
        select rev_id from prev_commit_id limit 1
        """
    )
    fun getPrevProcessedCommitId(): String

    @SqlUpdate(
        """
        update prev_commit_id set rev_id = :revId
        """
    )
    fun updatePrevProcessedCommitId(prevProcessedCommitId: String): Int


    @SqlBatch(
        """
        insert into ba_user (id, username) values (
            :id,
            :username
        ) on conflict(username) do update set id = :id 
        """
    )
    fun batchUpsertUser(@BindBean userList: List<User>): IntArray

    @SqlBatch(
        """
        insert into ba_topic (type, id,title,uid,dateline) values (
            :typeId,
            :t.id,
            :t.title,
            :t.uid,
            :t.dateline
        ) on conflict(type,id,uid) do update set title = :t.title
        where id = :t.id and type = :typeId and uid = :t.uid
    """
    )
    fun batchUpsertTopic(@Bind("typeId") typeId:Int, @BindBean("t") topicList: List<Topic>): IntArray

    @SqlBatch(
        """
        insert into ba_post (type, id, mid, uid,dateline) values (
            :typeId,
            :p.id,
            :p.mid,
            :p.user.id,
            :p.dateline
        ) on conflict(type,id,mid,uid) do nothing
    """
    )
    fun batchUpsertPost(@Bind("typeId") typeId: Int, @BindBean("p") topicList: List<Post>): IntArray

    @SqlBatch(
        """
            insert into ba_likes (type, mid, pid, value, total)
            values (:type,
                    :mid,
                    :pid,
                    :value,
                    :total
            ) on conflict(type,mid,pid,value) do update set total = :total
            where type = :type and mid = :mid and pid = :pid and value = :value
        """
    )
    fun batchUpsertLikes(@BindBean likeList: List<Like>): IntArray

}