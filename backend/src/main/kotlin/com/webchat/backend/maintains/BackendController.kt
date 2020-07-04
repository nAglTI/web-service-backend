package com.webchat.backend.maintains

import com.beust.klaxon.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult

@RestController
@RequestMapping("/api")
class BackendController {

    @GetMapping("/authorize")
    fun authorize(@RequestParam("username") username: String,
                  @RequestParam("password", defaultValue = "anon") password: String): Int {
        if (password == "anon") {
            val user = User(username)
            ResourcesLists.OnlineUserList.add(user)
            return user.id
        } else {
            ResourcesLists.RegistredList.forEach {
                if (it.mail == username && it.password == password) {
                    ResourcesLists.OnlineUserList.add(it)
                    return it.UpdateId()
                }
            }
        }
        return -1 // Error
    }

    @GetMapping("/get_rooms")
    fun get_rooms(@RequestParam("user_id") user_id: Int): MutableList<JsonObject> {
        var rooms: MutableList<JsonObject> = mutableListOf()
        ResourcesLists.RoomList.forEach{
            var json = JsonObject()
            json["room_id"] = it.id
            json["room_name"] = it.name
            json["user_count"] = it.UserCount
            json["room_picture"] = it.RoomPicture
            rooms.add(json)
        }
        return rooms
    }

    @GetMapping("/update_user_picture")
    fun update_user_picture(@RequestParam("user_id") user_id: Int,
                            @RequestParam("picture") picture: String) {
        val user = ResourcesLists.OnlineUserList.find { it.id == user_id }
        val context = JsonObject()
        if (user != null) {
            user.UserPicture = picture
            context["type"] = "user_picture_change"
            context["context"] = picture
        }
        ResourcesLists.OnlineUserList.forEach {
            it.AddToUpdateList(context)
        }
    }

    @GetMapping("/update_room_picture")
    fun update_room_picture(@RequestParam("user_id") user_id: Int,
                            @RequestParam("room_id") room_id: Int,
                            @RequestParam("picture") picture: String) {
        val user = ResourcesLists.OnlineUserList.find { it.id == user_id }
        val room = ResourcesLists.RoomList.find { it.id == room_id }
        val context = JsonObject()
        if (user != null && room != null) {
            room.RoomPicture = picture
            context["type"] = "room_picture_change"
            context["context"] = picture
        }
        ResourcesLists.OnlineUserList.forEach {
            it.AddToUpdateList(context)
        }
    }

    @GetMapping("/add_room")
    fun add_room(@RequestParam("user_id") user_id: Int,
                 @RequestParam("name") name: String): Int {
        val user = ResourcesLists.OnlineUserList.find { it.id == user_id }
        val context = JsonObject()
        ResourcesLists.RoomList.forEach {
            if (it.name == name)
                return 1 // Комната с таким именем уже существует
        }
        user!!.CreateRoom(name)
        val room = ResourcesLists.RoomList.find { it.name == name }
        context["type"] = "add_room"
        context["context"] = room!!.GetRoomInfo()
        ResourcesLists.OnlineUserList.forEach {
            it.AddToUpdateList(context)
        }

        return 0 // Комната создалась
    }

    @GetMapping("/leave_room")
    fun leave_room(@RequestParam("user_id") user_id: Int,
                   @RequestParam("room_id") room_id: Int) {
        val room = ResourcesLists.RoomList.find { it.id == room_id }
        val context = JsonObject()
        context["type"] = "remove_room"
        context["context"] = room!!.id
        if (room!!.UserCount == 1) {
            ResourcesLists.RoomList.remove(room)
            GenerateID.RoomIdNeNyzhen(room_id)
            room.UserLeaveRoom(user_id)
            ResourcesLists.OnlineUserList.forEach {
                it.AddToUpdateList(context)
            }
        } else {
            room.UserLeaveRoom(user_id)
        }
    }

    @GetMapping("/entry_room")
    fun entry_room(@RequestParam("user_id") user_id: Int,
                   @RequestParam("room_id") room_id: Int): Int {
        val user = ResourcesLists.FindUserById(user_id)
        ResourcesLists.RoomList.forEach {
            if (room_id == it.id && user != null) {
                it.AddToUserList(user)
                return 0
            }
        }
        return 1
    }

    @GetMapping("/register")
    fun register(@RequestParam("mail") mail: String,
                 @RequestParam("username") username: String,
                 @RequestParam("password") password: String): Int {
        ResourcesLists.RegistredList.forEach {
            if (it.mail == mail)
                return 1 // Такое мыло уже занято
        }
        val user = AuthUser(username, password)
        user.mail = mail
        ResourcesLists.RegistredList.add(user)
        return 0 // No error
    }

    @GetMapping("/get_uinfo")
    fun get_uinfo(@RequestParam("user_id") user_id: Int): JsonObject {
        val JsonInfo = JsonObject()
        val user: User? = ResourcesLists.OnlineUserList.find { it.id == user_id }
        if (user != null) {
            JsonInfo["Username"] = user.username
            JsonInfo["Rights"] = user.rights
            JsonInfo["Picture"] = user.UserPicture
            return JsonInfo
        }
        JsonInfo["error"] = "error"
        return JsonInfo
    }

    @GetMapping("/send_message")
    fun send_message(@RequestParam("user_id") user_id: Int,
                     @RequestParam("room_id") room_id: Int,
                     @RequestParam("message") message: String) {
        val room = ResourcesLists.RoomList.find { it.id == room_id }
        room?.SendMessage(user_id, message)
    }

    @GetMapping("/lp")
    fun lp(@RequestParam("user_id") user_id: Int): DeferredResult<JsonObject> {
        val user = ResourcesLists.OnlineUserList.find { it.id == user_id }
        val timeout = JsonObject()
        timeout["timeout"] = "timeout"
        var longpoll = user?.longpoll
        GlobalScope.launch {
            delay(40_000)
            if (longpoll == user?.longpoll) {
                ResourcesLists.OnlineUserList.remove(user)
                ResourcesLists.RoomList.forEach {
                    if (it.UserList.find { it.id == user_id } == user)
                        it.UserLeaveRoom(user_id)
                }
            }
        }
        val result = DeferredResult<JsonObject>(15000, timeout)
        user?.UpdateLongPoll(result)
        return result
    }
}
