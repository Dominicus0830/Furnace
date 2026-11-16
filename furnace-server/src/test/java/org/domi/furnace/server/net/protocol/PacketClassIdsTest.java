package org.domi.furnace.server.net.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;
import org.domi.furnace.server.advancement.SelectAdvancementsTab;
import org.domi.furnace.server.advancement.UpdateAdvancements;
import org.domi.furnace.server.command.CommandTreePacket;
import org.domi.furnace.server.net.protocol.configuration.BrandPayload;
import org.domi.furnace.server.net.protocol.configuration.ConfigDisconnect;
import org.domi.furnace.server.net.protocol.configuration.ConfigKeepAlive;
import org.domi.furnace.server.net.protocol.configuration.ConfigTransfer;
import org.domi.furnace.server.net.protocol.configuration.CookieRequest;
import org.domi.furnace.server.net.protocol.configuration.FeatureFlags;
import org.domi.furnace.server.net.protocol.configuration.FinishConfiguration;
import org.domi.furnace.server.net.protocol.configuration.KnownPacksClientbound;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.protocol.configuration.ResourcePackPop;
import org.domi.furnace.server.net.protocol.configuration.StoreCookie;
import org.domi.furnace.server.net.protocol.configuration.UpdateTags;
import org.domi.furnace.server.net.protocol.handshake.Handshake;
import org.domi.furnace.server.net.protocol.login.EncryptionRequest;
import org.domi.furnace.server.net.protocol.login.EncryptionResponse;
import org.domi.furnace.server.net.protocol.login.LoginDisconnect;
import org.domi.furnace.server.net.protocol.login.LoginPluginRequest;
import org.domi.furnace.server.net.protocol.login.LoginStart;
import org.domi.furnace.server.net.protocol.login.LoginSuccess;
import org.domi.furnace.server.net.protocol.login.SetCompression;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.AddExperienceOrb;
import org.domi.furnace.server.net.protocol.play.Animate;
import org.domi.furnace.server.net.protocol.play.AwardStats;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.BlockDestruction;
import org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket;
import org.domi.furnace.server.net.protocol.play.BlockEventPacket;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.BossEvent;
import org.domi.furnace.server.net.protocol.play.BundleDelimiter;
import org.domi.furnace.server.net.protocol.play.ChangeDifficulty;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.ChunksBiomes;
import org.domi.furnace.server.net.protocol.play.ClearTitles;
import org.domi.furnace.server.net.protocol.play.CommandSuggestionsResponse;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetData;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;
import org.domi.furnace.server.net.protocol.play.Cooldown;
import org.domi.furnace.server.net.protocol.play.CustomPayloadPacket;
import org.domi.furnace.server.net.protocol.play.DamageEvent;
import org.domi.furnace.server.net.protocol.play.DebugSample;
import org.domi.furnace.server.net.protocol.play.DeleteChat;
import org.domi.furnace.server.net.protocol.play.Disconnect;
import org.domi.furnace.server.net.protocol.play.DisguisedChatPacket;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.ExplodePacket;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.HorseScreenOpen;
import org.domi.furnace.server.net.protocol.play.HurtAnimation;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.KeepAliveClientbound;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.LevelEventPacket;
import org.domi.furnace.server.net.protocol.play.LevelParticles;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.MapItemData;
import org.domi.furnace.server.net.protocol.play.MerchantOffersPacket;
import org.domi.furnace.server.net.protocol.play.MoveEntityPos;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import org.domi.furnace.server.net.protocol.play.MoveVehicle;
import org.domi.furnace.server.net.protocol.play.OpenBook;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.net.protocol.play.OpenSignEditor;
import org.domi.furnace.server.net.protocol.play.PlaceGhostRecipe;
import org.domi.furnace.server.net.protocol.play.PlayPongResponse;
import org.domi.furnace.server.net.protocol.play.PlayerAbilities;
import org.domi.furnace.server.net.protocol.play.PlayerChatPacket;
import org.domi.furnace.server.net.protocol.play.PlayerCombatEnd;
import org.domi.furnace.server.net.protocol.play.PlayerCombatEnter;
import org.domi.furnace.server.net.protocol.play.PlayerCombatKill;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.PlayerLookAt;
import org.domi.furnace.server.net.protocol.play.RecipeBookUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.RemoveMobEffect;
import org.domi.furnace.server.net.protocol.play.ResetScore;
import org.domi.furnace.server.net.protocol.play.ResourcePackPush;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.RotateHead;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.protocol.play.ServerData;
import org.domi.furnace.server.net.protocol.play.ServerLinksPacket;
import org.domi.furnace.server.net.protocol.play.SetBorderCenter;
import org.domi.furnace.server.net.protocol.play.SetBorderLerpSize;
import org.domi.furnace.server.net.protocol.play.SetBorderSize;
import org.domi.furnace.server.net.protocol.play.SetBorderWarningDelay;
import org.domi.furnace.server.net.protocol.play.SetBorderWarningDistance;
import org.domi.furnace.server.net.protocol.play.SetCamera;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetChunkCacheRadius;
import org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition;
import org.domi.furnace.server.net.protocol.play.SetDisplayObjective;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetEntityLink;
import org.domi.furnace.server.net.protocol.play.SetEntityMotion;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.domi.furnace.server.net.protocol.play.SetExperience;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SetHealth;
import org.domi.furnace.server.net.protocol.play.SetHeldSlot;
import org.domi.furnace.server.net.protocol.play.SetObjective;
import org.domi.furnace.server.net.protocol.play.SetPassengers;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.net.protocol.play.SetScore;
import org.domi.furnace.server.net.protocol.play.SetSimulationDistance;
import org.domi.furnace.server.net.protocol.play.SetTime;
import org.domi.furnace.server.net.protocol.play.SoundEntity;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.StopSound;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.net.protocol.play.SystemChat;
import org.domi.furnace.server.net.protocol.play.TabListHeaderFooter;
import org.domi.furnace.server.net.protocol.play.TagQuery;
import org.domi.furnace.server.net.protocol.play.TakeItemEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.TickingState;
import org.domi.furnace.server.net.protocol.play.TickingStep;
import org.domi.furnace.server.net.protocol.play.TitleTimes;
import org.domi.furnace.server.net.protocol.play.Transfer;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.domi.furnace.server.net.protocol.play.UpdateMobEffect;
import org.domi.furnace.server.net.protocol.play.UpdateRecipes;
import org.domi.furnace.server.net.protocol.play.UpdateTagsPlay;
import org.domi.furnace.server.net.protocol.status.PongResponse;
import org.domi.furnace.server.net.protocol.status.StatusResponse;
import org.domi.furnace.server.net.protocol.play.MoveEntity;
import org.domi.furnace.server.net.protocol.play.TextOnlyPacket;
import org.domi.furnace.server.plugin.packet.ProtocolTable;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.ProtocolIds;
import org.junit.jupiter.api.Test;

/**
 * 서버 패킷 클래스의 번호(ID·ID_PLAY·ID_CONFIG)가 바닐라에서 뽑은 번호표(protocol-<프로토콜>-ids.txt)와 같은 번호를 말하는지.
 * 클래스마다 "어느 상태·방향의 어느 와이어 이름인가"를 손으로 적어 두고, 그 이름이 번호표에서 가리키는 번호와 비교한다.
 * 번호가 상수 생성(PacketIds)으로 바뀐 뒤에도 클래스와 와이어 이름의 짝이 맞는지 이 표가 잡는다.
 */
class PacketClassIdsTest {

    private record Row(Class<?> type, String field, String protocol, PacketDirection direction, String wireName) {}

    private static Row row(Class<?> type, String field, String protocol, PacketDirection direction, String wireName) {
        return new Row(type, field, protocol, direction, wireName);
    }

    private static final List<Row> ROWS = List.of(
            row(CookieRequest.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "cookie_request"),
            row(BrandPayload.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "custom_payload"),
            row(CustomPayloadPacket.class, "ID_CONFIG", "configuration", PacketDirection.CLIENTBOUND, "custom_payload"),
            row(ConfigDisconnect.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "disconnect"),
            row(FinishConfiguration.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "finish_configuration"),
            row(ConfigKeepAlive.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "keep_alive"),
            row(RegistryData.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "registry_data"),
            row(ResourcePackPop.class, "ID_CONFIG", "configuration", PacketDirection.CLIENTBOUND, "resource_pack_pop"),
            row(ResourcePackPush.class, "ID_CONFIG", "configuration", PacketDirection.CLIENTBOUND, "resource_pack_push"),
            row(StoreCookie.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "store_cookie"),
            row(ConfigTransfer.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "transfer"),
            row(FeatureFlags.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "update_enabled_features"),
            row(UpdateTags.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "update_tags"),
            row(KnownPacksClientbound.class, "ID", "configuration", PacketDirection.CLIENTBOUND, "select_known_packs"),
            row(ServerLinksPacket.class, "ID_CONFIG", "configuration", PacketDirection.CLIENTBOUND, "server_links"),
            row(BundleDelimiter.class, "ID", "game", PacketDirection.CLIENTBOUND, "bundle_delimiter"),
            row(AddEntity.class, "ID", "game", PacketDirection.CLIENTBOUND, "add_entity"),
            row(SpawnEntity.class, "ID", "game", PacketDirection.CLIENTBOUND, "add_entity"),
            row(AddExperienceOrb.class, "ID", "game", PacketDirection.CLIENTBOUND, "add_experience_orb"),
            row(Animate.class, "ID", "game", PacketDirection.CLIENTBOUND, "animate"),
            row(AwardStats.class, "ID", "game", PacketDirection.CLIENTBOUND, "award_stats"),
            row(BlockChangedAck.class, "ID", "game", PacketDirection.CLIENTBOUND, "block_changed_ack"),
            row(BlockDestruction.class, "ID", "game", PacketDirection.CLIENTBOUND, "block_destruction"),
            row(BlockEntityDataPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "block_entity_data"),
            row(BlockEventPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "block_event"),
            row(BlockUpdate.class, "ID", "game", PacketDirection.CLIENTBOUND, "block_update"),
            row(BossEvent.class, "ID", "game", PacketDirection.CLIENTBOUND, "boss_event"),
            row(ChangeDifficulty.class, "ID", "game", PacketDirection.CLIENTBOUND, "change_difficulty"),
            row(ChunkBatchFinished.class, "ID", "game", PacketDirection.CLIENTBOUND, "chunk_batch_finished"),
            row(ChunkBatchStart.class, "ID", "game", PacketDirection.CLIENTBOUND, "chunk_batch_start"),
            row(ChunksBiomes.class, "ID", "game", PacketDirection.CLIENTBOUND, "chunks_biomes"),
            row(ClearTitles.class, "ID", "game", PacketDirection.CLIENTBOUND, "clear_titles"),
            row(CommandSuggestionsResponse.class, "ID", "game", PacketDirection.CLIENTBOUND, "command_suggestions"),
            row(CommandTreePacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "commands"),
            row(ContainerClose.class, "ID", "game", PacketDirection.CLIENTBOUND, "container_close"),
            row(ContainerSetContent.class, "ID", "game", PacketDirection.CLIENTBOUND, "container_set_content"),
            row(ContainerSetData.class, "ID", "game", PacketDirection.CLIENTBOUND, "container_set_data"),
            row(ContainerSetSlot.class, "ID", "game", PacketDirection.CLIENTBOUND, "container_set_slot"),
            row(Cooldown.class, "ID", "game", PacketDirection.CLIENTBOUND, "cooldown"),
            row(CustomPayloadPacket.class, "ID_PLAY", "game", PacketDirection.CLIENTBOUND, "custom_payload"),
            row(DamageEvent.class, "ID", "game", PacketDirection.CLIENTBOUND, "damage_event"),
            row(DebugSample.class, "ID", "game", PacketDirection.CLIENTBOUND, "debug_sample"),
            row(DeleteChat.class, "ID", "game", PacketDirection.CLIENTBOUND, "delete_chat"),
            row(Disconnect.class, "ID", "game", PacketDirection.CLIENTBOUND, "disconnect"),
            row(DisguisedChatPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "disguised_chat"),
            row(EntityEvent.class, "ID", "game", PacketDirection.CLIENTBOUND, "entity_event"),
            row(ExplodePacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "explode"),
            row(UnloadChunk.class, "ID", "game", PacketDirection.CLIENTBOUND, "forget_level_chunk"),
            row(GameEvent.class, "ID", "game", PacketDirection.CLIENTBOUND, "game_event"),
            row(HorseScreenOpen.class, "ID", "game", PacketDirection.CLIENTBOUND, "horse_screen_open"),
            row(HurtAnimation.class, "ID", "game", PacketDirection.CLIENTBOUND, "hurt_animation"),
            row(InitializeBorder.class, "ID", "game", PacketDirection.CLIENTBOUND, "initialize_border"),
            row(KeepAliveClientbound.class, "ID", "game", PacketDirection.CLIENTBOUND, "keep_alive"),
            row(LevelChunkWithLight.class, "ID", "game", PacketDirection.CLIENTBOUND, "level_chunk_with_light"),
            row(LevelEventPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "level_event"),
            row(LevelParticles.class, "ID", "game", PacketDirection.CLIENTBOUND, "level_particles"),
            row(LightUpdate.class, "ID", "game", PacketDirection.CLIENTBOUND, "light_update"),
            row(LoginPlay.class, "ID", "game", PacketDirection.CLIENTBOUND, "login"),
            row(MapItemData.class, "ID", "game", PacketDirection.CLIENTBOUND, "map_item_data"),
            row(MerchantOffersPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "merchant_offers"),
            row(MoveEntityPos.class, "ID", "game", PacketDirection.CLIENTBOUND, "move_entity_pos"),
            row(MoveEntityPosRot.class, "ID", "game", PacketDirection.CLIENTBOUND, "move_entity_pos_rot"),
            row(MoveEntityRot.class, "ID", "game", PacketDirection.CLIENTBOUND, "move_entity_rot"),
            row(MoveVehicle.class, "ID", "game", PacketDirection.CLIENTBOUND, "move_vehicle"),
            row(OpenBook.class, "ID", "game", PacketDirection.CLIENTBOUND, "open_book"),
            row(OpenScreen.class, "ID", "game", PacketDirection.CLIENTBOUND, "open_screen"),
            row(OpenSignEditor.class, "ID", "game", PacketDirection.CLIENTBOUND, "open_sign_editor"),
            row(PlayPongResponse.class, "ID", "game", PacketDirection.CLIENTBOUND, "pong_response"),
            row(PlaceGhostRecipe.class, "ID", "game", PacketDirection.CLIENTBOUND, "place_ghost_recipe"),
            row(PlayerAbilities.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_abilities"),
            row(PlayerChatPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_chat"),
            row(PlayerCombatEnd.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_combat_end"),
            row(PlayerCombatEnter.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_combat_enter"),
            row(PlayerCombatKill.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_combat_kill"),
            row(PlayerInfoRemove.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_info_remove"),
            row(PlayerInfoUpdate.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_info_update"),
            row(PlayerLookAt.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_look_at"),
            row(SyncPlayerPosition.class, "ID", "game", PacketDirection.CLIENTBOUND, "player_position"),
            row(RecipeBookUpdate.class, "ID", "game", PacketDirection.CLIENTBOUND, "recipe"),
            row(RemoveEntities.class, "ID", "game", PacketDirection.CLIENTBOUND, "remove_entities"),
            row(RemoveMobEffect.class, "ID", "game", PacketDirection.CLIENTBOUND, "remove_mob_effect"),
            row(ResetScore.class, "ID", "game", PacketDirection.CLIENTBOUND, "reset_score"),
            row(ResourcePackPop.class, "ID_PLAY", "game", PacketDirection.CLIENTBOUND, "resource_pack_pop"),
            row(ResourcePackPush.class, "ID_PLAY", "game", PacketDirection.CLIENTBOUND, "resource_pack_push"),
            row(Respawn.class, "ID", "game", PacketDirection.CLIENTBOUND, "respawn"),
            row(RotateHead.class, "ID", "game", PacketDirection.CLIENTBOUND, "rotate_head"),
            row(SetHeadRotation.class, "ID", "game", PacketDirection.CLIENTBOUND, "rotate_head"),
            row(SectionBlocksUpdate.class, "ID", "game", PacketDirection.CLIENTBOUND, "section_blocks_update"),
            row(SelectAdvancementsTab.class, "ID", "game", PacketDirection.CLIENTBOUND, "select_advancements_tab"),
            row(ServerData.class, "ID", "game", PacketDirection.CLIENTBOUND, "server_data"),
            row(SetBorderCenter.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_border_center"),
            row(SetBorderLerpSize.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_border_lerp_size"),
            row(SetBorderSize.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_border_size"),
            row(SetBorderWarningDelay.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_border_warning_delay"),
            row(SetBorderWarningDistance.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_border_warning_distance"),
            row(SetCamera.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_camera"),
            row(SetHeldSlot.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_carried_item"),
            row(SetCenterChunk.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_chunk_cache_center"),
            row(SetChunkCacheRadius.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_chunk_cache_radius"),
            row(SetDefaultSpawnPosition.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_default_spawn_position"),
            row(SetDisplayObjective.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_display_objective"),
            row(SetEntityData.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_entity_data"),
            row(SetEntityLink.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_entity_link"),
            row(SetEntityMotion.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_entity_motion"),
            row(SetEquipment.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_equipment"),
            row(SetExperience.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_experience"),
            row(SetHealth.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_health"),
            row(SetObjective.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_objective"),
            row(SetPassengers.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_passengers"),
            row(SetPlayerTeam.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_player_team"),
            row(SetScore.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_score"),
            row(SetSimulationDistance.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_simulation_distance"),
            row(SetTime.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_time"),
            row(TitleTimes.class, "ID", "game", PacketDirection.CLIENTBOUND, "set_titles_animation"),
            row(SoundEntity.class, "ID", "game", PacketDirection.CLIENTBOUND, "sound_entity"),
            row(SoundPacket.class, "ID", "game", PacketDirection.CLIENTBOUND, "sound"),
            row(StopSound.class, "ID", "game", PacketDirection.CLIENTBOUND, "stop_sound"),
            row(SystemChat.class, "ID", "game", PacketDirection.CLIENTBOUND, "system_chat"),
            row(TabListHeaderFooter.class, "ID", "game", PacketDirection.CLIENTBOUND, "tab_list"),
            row(TagQuery.class, "ID", "game", PacketDirection.CLIENTBOUND, "tag_query"),
            row(TakeItemEntity.class, "ID", "game", PacketDirection.CLIENTBOUND, "take_item_entity"),
            row(TeleportEntity.class, "ID", "game", PacketDirection.CLIENTBOUND, "teleport_entity"),
            row(TickingState.class, "ID", "game", PacketDirection.CLIENTBOUND, "ticking_state"),
            row(TickingStep.class, "ID", "game", PacketDirection.CLIENTBOUND, "ticking_step"),
            row(Transfer.class, "ID", "game", PacketDirection.CLIENTBOUND, "transfer"),
            row(UpdateAdvancements.class, "ID", "game", PacketDirection.CLIENTBOUND, "update_advancements"),
            row(UpdateAttributes.class, "ID", "game", PacketDirection.CLIENTBOUND, "update_attributes"),
            row(UpdateMobEffect.class, "ID", "game", PacketDirection.CLIENTBOUND, "update_mob_effect"),
            row(UpdateRecipes.class, "ID", "game", PacketDirection.CLIENTBOUND, "update_recipes"),
            row(UpdateTagsPlay.class, "ID", "game", PacketDirection.CLIENTBOUND, "update_tags"),
            row(ServerLinksPacket.class, "ID_PLAY", "game", PacketDirection.CLIENTBOUND, "server_links"),
            row(Handshake.class, "ID", "handshake", PacketDirection.SERVERBOUND, "intention"),
            row(LoginDisconnect.class, "ID", "login", PacketDirection.CLIENTBOUND, "login_disconnect"),
            row(LoginStart.class, "ID", "login", PacketDirection.SERVERBOUND, "hello"),
            row(EncryptionRequest.class, "ID", "login", PacketDirection.CLIENTBOUND, "hello"),
            row(EncryptionResponse.class, "ID", "login", PacketDirection.SERVERBOUND, "key"),
            row(LoginSuccess.class, "ID", "login", PacketDirection.CLIENTBOUND, "game_profile"),
            row(SetCompression.class, "ID", "login", PacketDirection.CLIENTBOUND, "login_compression"),
            row(LoginPluginRequest.class, "ID", "login", PacketDirection.CLIENTBOUND, "custom_query"),
            row(StatusResponse.class, "ID", "status", PacketDirection.CLIENTBOUND, "status_response"),
            row(PongResponse.class, "ID", "status", PacketDirection.CLIENTBOUND, "pong_response"),
            row(TextOnlyPacket.class, "ACTION_BAR", "game", PacketDirection.CLIENTBOUND, "set_action_bar_text"),
            row(TextOnlyPacket.class, "TITLE", "game", PacketDirection.CLIENTBOUND, "set_title_text"),
            row(TextOnlyPacket.class, "SUBTITLE", "game", PacketDirection.CLIENTBOUND, "set_subtitle_text"),
            row(MoveEntity.class, "POS", "game", PacketDirection.CLIENTBOUND, "move_entity_pos"),
            row(MoveEntity.class, "POS_ROT", "game", PacketDirection.CLIENTBOUND, "move_entity_pos_rot"),
            row(MoveEntity.class, "ROT", "game", PacketDirection.CLIENTBOUND, "move_entity_rot"),
            row(ProtocolTable.class, "MOVE_POS", "game", PacketDirection.SERVERBOUND, "move_player_pos"),
            row(ProtocolTable.class, "MOVE_POS_ROT", "game", PacketDirection.SERVERBOUND, "move_player_pos_rot"),
            row(ProtocolTable.class, "MOVE_ROT", "game", PacketDirection.SERVERBOUND, "move_player_rot"),
            row(ProtocolTable.class, "MOVE_STATUS", "game", PacketDirection.SERVERBOUND, "move_player_status_only")
    );

    private static int tableId(Row r) {
        var hits = ProtocolIds.current().entries(r.protocol(), r.direction()).stream()
                .filter(e -> e.wireName().equals(r.wireName())).toList();
        assertEquals(1, hits.size(), "번호표에서 이름이 하나여야 한다: " + r);
        return hits.get(0).id();
    }

    @Test
    void everyPacketClassIdEqualsTheTableNumberOfItsWireName() throws Exception {
        for (Row r : ROWS) {
            Field f = r.type().getDeclaredField(r.field());
            f.setAccessible(true);
            assertEquals(tableId(r), f.getInt(null), r.type().getSimpleName() + "." + r.field() + " = " + r.protocol() + "/" + r.direction() + "/" + r.wireName());
        }
    }
}
