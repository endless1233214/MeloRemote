import XCTest
@testable import MeloRemote

final class ModelTests: XCTestCase {
    func testServerAddressNormalization() throws {
        let bare = try MusicAssistantClient.normalizedServerURL(from: "musicassistant.local:8095")
        XCTAssertEqual(bare.absoluteString, "http://musicassistant.local:8095")

        let trailingSlash = try MusicAssistantClient.normalizedServerURL(
            from: " http://musicassistant.local:8095/ "
        )
        XCTAssertEqual(trailingSlash.absoluteString, "http://musicassistant.local:8095")
    }

    func testLoginPayloadDecodesTokenKeyVariants() throws {
        let accessTokenJSON = #"{"access_token":"abc","success":true}"#
        let accessToken = try JSONDecoder().decode(
            MALoginResponse.self,
            from: Data(accessTokenJSON.utf8)
        )
        XCTAssertEqual(accessToken.resolvedAccessToken, "abc")

        let tokenJSON = #"{"token":"def","success":true}"#
        let token = try JSONDecoder().decode(
            MALoginResponse.self,
            from: Data(tokenJSON.utf8)
        )
        XCTAssertEqual(token.resolvedAccessToken, "def")

        let currentJSON = """
        {
          "success": true,
          "token": "ghi",
          "user": {
            "user_id": "user-1",
            "username": "admin",
            "display_name": "Admin",
            "role": "admin"
          }
        }
        """
        let current = try JSONDecoder().decode(
            MALoginResponse.self,
            from: Data(currentJSON.utf8)
        )
        XCTAssertEqual(current.resolvedAccessToken, "ghi")
        XCTAssertEqual(current.user?.title, "Admin")
    }

    func testAccountLoginRequestUsesCurrentCredentialsPayloadFirst() throws {
        let url = try XCTUnwrap(URL(string: "http://musicassistant.local:8095/auth/login"))
        let requests = try MusicAssistantClient.loginRequests(
            for: url,
            username: "admin",
            password: "correct horse"
        )

        XCTAssertEqual(requests.count, 3)
        XCTAssertEqual(requests[0].httpMethod, "POST")
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Content-Type"), "application/json")

        let data = try XCTUnwrap(requests[0].httpBody)
        let object = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )
        XCTAssertEqual(object["provider_id"] as? String, "builtin")
        XCTAssertEqual(object["device_name"] as? String, "MeloRemote")

        let credentials = try XCTUnwrap(object["credentials"] as? [String: String])
        XCTAssertEqual(credentials["username"], "admin")
        XCTAssertEqual(credentials["password"], "correct horse")
    }

    func testImageProxySizesUseAllowedMusicAssistantValues() {
        XCTAssertEqual(AppModel.normalizedImageProxySize(0), 0)
        XCTAssertEqual(AppModel.normalizedImageProxySize(1), 80)
        XCTAssertEqual(AppModel.normalizedImageProxySize(160), 160)
        XCTAssertEqual(AppModel.normalizedImageProxySize(420), 512)
        XCTAssertEqual(AppModel.normalizedImageProxySize(700), 1024)
        XCTAssertEqual(AppModel.normalizedImageProxySize(900), 1024)
    }

    func testInvalidServerAddressIsRejected() {
        XCTAssertThrowsError(try MusicAssistantClient.normalizedServerURL(from: "not a host"))
        XCTAssertThrowsError(try MusicAssistantClient.normalizedServerURL(from: "ftp://server.local"))
    }

    func testQueuePayloadDecodes() throws {
        let json = """
        {
          "queue_id":"speaker",
          "available":true,
          "items":4,
          "shuffle_enabled":false,
          "repeat_mode":"all",
          "elapsed_time":20,
          "elapsed_time_last_updated":1000,
          "state":"paused",
          "current_item":{
            "queue_item_id":"track-1",
            "name":"Birds of a Feather",
            "duration":210,
            "media_item":{
              "item_id":"3",
              "provider":"library",
              "name":"Birds of a Feather",
              "media_type":"track",
              "uri":"library://track/3",
              "artists":[{"name":"Billie Eilish"}]
            }
          }
        }
        """

        let queue = try JSONDecoder().decode(MAPlayerQueue.self, from: Data(json.utf8))
        XCTAssertEqual(queue.queueID, "speaker")
        XCTAssertEqual(queue.currentItem?.title, "Birds of a Feather")
        XCTAssertEqual(queue.currentItem?.subtitle, "Billie Eilish")
        XCTAssertEqual(queue.correctedElapsedTime, 20)
    }

    func testSearchPayloadUsesAllMediaSections() throws {
        let json = """
        {
          "artists":[],
          "albums":[],
          "tracks":[{
            "item_id":"1",
            "provider":"library",
            "name":"Track",
            "media_type":"track",
            "uri":"library://track/1"
          }],
          "playlists":[],
          "podcasts":[],
          "audiobooks":[],
          "radio":[]
        }
        """

        let result = try JSONDecoder().decode(MASearchResult.self, from: Data(json.utf8))
        XCTAssertEqual(result.tracks?.first?.uri, "library://track/1")
        XCTAssertEqual(result.tracks?.first?.title, "Track")
    }

    func testMediaItemCapabilityHelpersMatchMusicAssistantActions() throws {
        let track: MAMediaItem = try decodeMediaItem(
            """
            {
              "item_id":"1",
              "provider":"library",
              "name":"Track",
              "media_type":"track",
              "uri":"library://track/1",
              "favorite":false
            }
            """
        )
        XCTAssertTrue(track.isInLibrary)
        XCTAssertTrue(track.canPlay)
        XCTAssertTrue(track.canStartRadio)
        XCTAssertTrue(track.canBeFavorited)
        XCTAssertTrue(track.canChangeLibraryMembership)
        XCTAssertTrue(track.canBeAddedToPlaylist)

        let dynamicPlaylist: MAMediaItem = try decodeMediaItem(
            """
            {
              "item_id":"2",
              "provider":"library",
              "name":"Smart Mix",
              "media_type":"playlist",
              "uri":"library://playlist/2",
              "is_dynamic":true
            }
            """
        )
        XCTAssertFalse(dynamicPlaylist.canStartRadio)

        let radio: MAMediaItem = try decodeMediaItem(
            """
            {
              "item_id":"3",
              "provider":"radio",
              "name":"Station",
              "media_type":"radio",
              "uri":"radio://station/3"
            }
            """
        )
        XCTAssertFalse(radio.isInLibrary)
        XCTAssertFalse(radio.canStartRadio)
        XCTAssertTrue(radio.canBeAddedToPlaylist)
        XCTAssertTrue(radio.canChangeLibraryMembership)
    }

    func testJSONValueRoundTrip() throws {
        let value: JSONValue = .object([
            "command": .string("players/all"),
            "args": .object([:]),
            "enabled": .bool(true),
            "limit": .number(20)
        ])

        let data = try JSONEncoder().encode(value)
        let decoded = try JSONDecoder().decode(JSONValue.self, from: data)
        XCTAssertEqual(value, decoded)
        XCTAssertEqual(decoded["command"]?.stringValue, "players/all")
    }

    private func decodeMediaItem(_ json: String) throws -> MAMediaItem {
        try JSONDecoder().decode(MAMediaItem.self, from: Data(json.utf8))
    }
}
