package moe.nyamori.bgm.util

object VotingCalculator {

    fun totalPeople(arr: IntArray): Int = arr.sum()

    fun avg(arr: IntArray): Double {
        var totalPoint = 0.0
        var totalPeople = 0.0
        for (i in 1..10) {
            totalPoint += arr[i] * i
            totalPeople += arr[i]
        }
        return totalPoint / totalPeople
    }

    fun variance(arr: IntArray): Double {
        var totalPoint = 0.0
        var totalPeople = 0.0
        for (i in 1..10) {
            totalPoint += arr[i] * i
            totalPeople += arr[i]
        }
        val avg = totalPoint / totalPeople
        var s2 = 0.0
        for (i in 1..10) {
            s2 += (Math.pow((avg - i), 2.0) * arr[i]) / totalPeople
        }
        return s2
    }

    fun RMS(arr: IntArray): Double = Math.sqrt(variance(arr))
}