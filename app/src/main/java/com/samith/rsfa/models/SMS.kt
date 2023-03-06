package com.samith.rsfa.models
/**
 * Created by samithchow on 3/7/2023.
 */
data class SMS(
    var id: Int = 1,
    var name: String,
    var mobile: String,
    var from : String,
    var body : String,
    var time : Long,
)
