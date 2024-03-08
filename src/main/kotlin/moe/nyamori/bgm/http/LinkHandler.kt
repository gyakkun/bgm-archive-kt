package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler

object LinkHandler : Handler {

    override fun handle(ctx: Context) {
        ctx.html(html)
    }

    const val html = """
<html>

<body>
    <div id="content">Loading...</div>
</body>
<script type="text/javascript">
    const onErr = () => {
        let content = document.getElementById("content")
        content.innerHTML = "Error"
    }
    const onNoContent = () => {
        let content = document.getElementById("content")
        content.innerHTML = "No content"
    }
    const getTimeline = async () => {
        let path = document.location.pathname
        let timelineList = []
        // let jsonListPath = path.replace("/link", "")
        let jsonListPath = path.replace("/link", "?isHtml=true")
        timelineList = await fetch(jsonListPath)
            .then(d => { return d.text() })
            .then(t => { return JSON.parse(t) })
            .catch(e => { onErr(); console.error("Ex: ", e) })
        return timelineList
    }

    const go = async () => {
        let timelineJsonArr = await getTimeline()
        if (timelineJsonArr.length == 0) {
            onNoContent()
            return
        }
        let cele = document.getElementById("content")
        let ulele = document.createElement("ul")
        cele.innerHTML = "" + timelineJsonArr.length + " capture" 
                + ( (timelineJsonArr.length > 1) ? "s" : "" ) 
                + " in total:"
        cele.appendChild(ulele)
        for(idx in timelineJsonArr) {
            let ts = timelineJsonArr[idx]
            let li = document.createElement("li")
            let a = document.createElement("a")
            a.innerHTML = new Date(ts).toLocaleString()
            a.setAttribute("href", document.location.pathname.replace("/link", "/") + ts + "/html")
            li.appendChild(a)
            if (idx > 0 && new Date(ts).toLocaleDateString() != new Date(timelineJsonArr[idx-1]).toLocaleDateString()) {
                let hrzn = document.createElement("div")
                hrzn.innerHTML = "---------------------"
                ulele.appendChild(hrzn)
            }
            ulele.appendChild(li)
        }

    }
    go()
</script>
<style type="text/css">
    body {
        color: #222;
        background: #fff;
        font: 100% system-ui;
    }

    a {
        color: #0033cc;
    }

    @media (prefers-color-scheme: dark) {
        body {
            color: #eee;
            background: #121212;
        }

        body a {
            color: #afc3ff;
        }
    }
</style>

</html>
            """


}