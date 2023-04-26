package moe.nyamori.bgm.db

import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.model.Like
import moe.nyamori.bgm.model.Post
import moe.nyamori.bgm.model.Topic
import moe.nyamori.bgm.model.User
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.jdbi.v3.sqlobject.transaction.Transactional

@JvmDefaultWithCompatibility
interface BgmDao : Transactional<BgmDao> {

    @SqlQuery(
        """
        select 1
    """
    )
    fun healthCheck(): Int


    @SqlUpdate(
        """
        insert into meta_data (k,v) values (:k,:v) on conflict(k) do update set v = :v
        """
    )
    @Transaction
    fun upsertMetaData(@Bind("k") k: String, @Bind("v") v: String): Int

    @SqlQuery(
        """
        select v from meta_data where k = :k
        """
    )
    fun getMetaData(@Bind("k") k: String): String

    fun updatePrevProcessedCommitId(prevProcessedCommitId: String): Int {
        return upsertMetaData(Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME, prevProcessedCommitId)
    }

    fun getPrevProcessedCommitId(): String {
        return getMetaData(Config.BGM_ARCHIVE_PREV_PROCESSED_COMMIT_REV_ID_FILE_NAME)
    }


    @SqlBatch(
        """
        insert into ba_user (id, username) values (
            :id,
            :username
        ) on conflict(id) do update set username = :username 
        """
    )
    @Transaction
    fun batchUpsertUser(@BindBean userList: List<User>): IntArray

    @SqlBatch(
        """
        insert into ba_topic (type, id,uid,sid, title,dateline) values (
            :typeId,
            :t.id,
            :t.uid,
            :t.sid,
            :t.title,
            :t.dateline
        ) on conflict(type,id,uid) do update set title = :t.title, sid = :t.sid, dateline = :t.dateline
    """
    )
    @Transaction
    fun batchUpsertTopic(@Bind("typeId") typeId: Int, @BindBean("t") topicList: List<Topic>): IntArray

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
    @Transaction
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
        """
    )
    @Transaction
    fun batchUpsertLikes(@BindBean likeList: List<Like>): IntArray

}