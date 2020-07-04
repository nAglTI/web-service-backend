package com.webchat.backend.maintains

import com.beust.klaxon.JsonObject
import kotlinx.serialization.Serializable
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.atomic.AtomicInteger

object GenerateID {
    var LastUserId = AtomicInteger(0)
    var LastRoomId = AtomicInteger(0)
    var FreeUserIdList: MutableList<Int> = mutableListOf()
    var FreeRoomIdList: MutableList<Int> = mutableListOf()
    fun getNewRID(): Int {
        if (FreeRoomIdList.isEmpty())
            return LastRoomId.incrementAndGet()
        var id = FreeRoomIdList.first()
        FreeRoomIdList.remove(id)
        return id
    }
    fun RoomIdNeNyzhen(id: Int) {
        FreeRoomIdList.add(id)
    }
    fun getNewUID(): Int {
        if (FreeUserIdList.isEmpty())
            return LastUserId.incrementAndGet()
        var id = FreeUserIdList.first()
        FreeUserIdList.remove(id)
        return id
    }
    fun UserIdNeNyzhen(id: Int) {
        FreeUserIdList.add(id)
    }
}

object ResourcesLists {
    var RoomList: MutableList<Room> = mutableListOf()
    var OnlineUserList: MutableList<User> = mutableListOf()
    var RegistredList: MutableList<AuthUser> = mutableListOf()

    fun FindUserById(id: Int): User? = OnlineUserList.find { it.id == id }
}

object UserRights {
    val basic_right = 1 shl 0
    val advanced_right = 1 shl 1
    val god_right = 1 shl 2
}

object GenerateJson {
    fun LeaveUser(user_id: Int): JsonObject {
        val json = JsonObject()
        json["type"] = "remove_user"
        json["context"] = user_id
        return json
    }

    fun NewMessage(message: String): JsonObject {
        val json = JsonObject()
        json["type"] = "new_message"
        json["context"] = message
        return json
    }
}

open class User(var username: String) {
    private var UpdateList: MutableList<JsonObject> = mutableListOf()
    var UserPicture: String = ""
    var id = GenerateID.getNewUID()
    var rights: Int = UserRights.basic_right
    var longpoll: DeferredResult<JsonObject>? = null

    fun CreateRoom(name: String) {
        val new_room = Room(GenerateID.getNewRID(), name)
        new_room.AddToUserList(this)
        ResourcesLists.RoomList.add(new_room)
    }

    fun UpdateLongPoll(result: DeferredResult<JsonObject>) {
        longpoll = result
        if (UpdateList.isNotEmpty()) {
            LongpollSend()
        }
    }

    fun LongpollSend() {
        val Jsonresult = JsonObject()
        var i = 0
        UpdateList.forEach {
            Jsonresult[i++.toString()] = it
        }
        if (longpoll?.setResult(Jsonresult) == true)
            UpdateList.clear()
    }

    fun AddToUpdateList(event: JsonObject) {
        UpdateList.add(event)
        LongpollSend()
    }
}

class AuthUser(username: String, var password: String) :
        User(username) {
    var mail: String = ""

    init {
        rights = UserRights.basic_right + UserRights.advanced_right
    }

    fun UpdateId(): Int {
        id = GenerateID.getNewUID()
        return id
    }
}

@Serializable
class Room(var id: Int, var name: String) {
    var UserList: MutableList<User> = mutableListOf()
    var UserCount: Int = UserList.size
    var RoomPicture: String = ""

    fun UserLeaveRoom(id: Int) {
        val user_leave = GenerateJson.LeaveUser(id)
        val roomcontext = JsonObject()
        val contextToAll = JsonObject()

        roomcontext["room_id"] = this.id
        roomcontext["type"] = "online_change"
        roomcontext["context"] = this.UserCount - 1

        contextToAll["type"] = "room_update"
        contextToAll["context"] = roomcontext

        UserList.forEach {
            if (it.id == id)
                UserList.remove(it)
            if (it.id != id)
                it.AddToUpdateList(user_leave)
        }
        ResourcesLists.OnlineUserList.forEach {
            it.AddToUpdateList(contextToAll)
        }
    }



    fun SendMessage(id: Int, message: String) {
        val new_message = GenerateJson.NewMessage(message)
        UserList.forEach {
            if (it.id != id)
                it.AddToUpdateList(new_message)
        }
    }

    fun GetRoomInfo(): JsonObject {
        val roomcontext = JsonObject()
        roomcontext["room_id"] = this.id
        roomcontext["user_count"] = this.UserCount
        roomcontext["room_name"] = this.name
        roomcontext["room_picture"] = this.RoomPicture
        return roomcontext
    }

    fun AddToUserList(user: User) {
        UserList.add(user)

        val roomcontext = JsonObject()
        val contextToAll = JsonObject()
        roomcontext["room_id"] = this.id
        roomcontext["type"] = "online_change"
        roomcontext["context"] = this.UserCount

        contextToAll["type"] = "room_update"
        contextToAll["context"] = roomcontext

        ResourcesLists.OnlineUserList.forEach {
            it.AddToUpdateList(contextToAll)
        }
    }

}
