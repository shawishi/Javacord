package de.btobastian.javacord.entities.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.ImplDiscordApi;
import de.btobastian.javacord.entities.Activity;
import de.btobastian.javacord.entities.Icon;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.UserStatus;
import de.btobastian.javacord.entities.channels.PrivateChannel;
import de.btobastian.javacord.entities.channels.VoiceChannel;
import de.btobastian.javacord.entities.channels.impl.ImplPrivateChannel;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.utils.logging.LoggerUtil;
import de.btobastian.javacord.utils.rest.RestEndpoint;
import de.btobastian.javacord.utils.rest.RestMethod;
import de.btobastian.javacord.utils.rest.RestRequest;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The implementation of {@link User}.
 */
public class ImplUser implements User {

    /**
     * The logger of this class.
     */
    Logger logger = LoggerUtil.getLogger(ImplUser.class);

    /**
     * The discord api instance.
     */
    private final ImplDiscordApi api;

    /**
     * The id of the user.
     */
    private final long id;

    /**
     * The name of the user.
     */
    private String name;

    /**
     * The private channel with the given user.
     */
    private PrivateChannel channel = null;

    /**
     * The avatar hash of the user. Might be <code>null</code>!
     */
    private String avatarHash = null;

    /**
     * The discriminator of the user.
     */
    private String discriminator;

    /**
     * Whether the user is a bot account or not.
     */
    private final boolean bot;

    /**
     * The activity of the user.
     */
    private Activity activity = null;

    /**
     * The voice-channel the user is connected to.
     */
    private VoiceChannel connectedVoiceChannel = null;

    /**
     * The status of the user.
     */
    private UserStatus status = UserStatus.OFFLINE;

    /**
     * Creates a new user.
     *
     * @param api The discord api instance.
     * @param data The json data of the user.
     * @param addToCache Determines whether this should be cached.
     */
    public ImplUser(ImplDiscordApi api, JsonNode data, boolean addToCache) {
        this.api = api;

        id = Long.parseLong(data.get("id").asText());
        name = data.get("username").asText();
        discriminator = data.get("discriminator").asText();
        if (data.has("avatar") && !data.get("avatar").isNull()) {
            avatarHash = data.get("avatar").asText();
        }
        bot = data.has("bot") && data.get("bot").asBoolean();
        if (addToCache) {
            api.addUserToCache(this);
        }
    }

    public ImplUser(ImplDiscordApi api, JsonNode data) {
        this(api, data, true);
    }

    /**
     * Sets the private channel with the user.
     *
     * @param channel The channel to set.
     */
    public void setChannel(PrivateChannel channel) {
        this.channel = channel;
    }

    /**
     * Sets the activity of the user.
     *
     * @param activity The activity to set.
     */
    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    /**
     * Sets the status of the user.
     *
     * @param status The status to set.
     */
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * Sets the name of the user.
     *
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the avatar hash of the user.
     * Might be <code>null</code>.
     *
     * @return The avatar hash of the user.
     */
    public String getAvatarHash() {
        return avatarHash;
    }

    /**
     * Sets the avatar hash of the user.
     *
     * @param avatarHash The avatar hash to set.
     */
    public void setAvatarHash(String avatarHash) {
        this.avatarHash = avatarHash;
    }

    /**
     * Gets or creates a new private channel.
     *
     * @param data The data of the private channel.
     * @return The private channel for the given data.
     */
    public PrivateChannel getOrCreateChannel(JsonNode data) {
        synchronized (this) {
            if (channel != null) {
                return channel;
            }
            return new ImplPrivateChannel(api, data);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDiscriminator() {
        return discriminator;
    }

    @Override
    public boolean isBot() {
        return bot;
    }

    @Override
    public Optional<Activity> getActivity() {
        return Optional.ofNullable(activity);
    }

    @Override
    public Optional<VoiceChannel> getConnectedVoiceChannel() {
        return Optional.ofNullable(connectedVoiceChannel);
    }

    /**
     * Sets the voice-channel this user is connected to. Might be <code>null</code>.
     *
     * @param channel The voice-channel this user is connected to.
     */
    public void setConnectedVoiceChannel(VoiceChannel channel) {
        connectedVoiceChannel = channel;
    }

    @Override
    public UserStatus getStatus() {
        return status;
    }

    @Override
    public Icon getAvatar() {
        String url = "https://cdn.discordapp.com/embed/avatars/" + Integer.parseInt(discriminator) % 5 + ".png";
        if (avatarHash != null) {
            url = "https://cdn.discordapp.com/avatars/" + getId() + "/" + avatarHash +
                    (avatarHash.startsWith("a_") ? ".gif" : ".png");
        }
        try {
            return new ImplIcon(getApi(), new URL(url));
        } catch (MalformedURLException e) {
            logger.warn("Seems like the url of the avatar is malformed! Please contact the developer!", e);
            return null;
        }
    }

    @Override
    public boolean hasDefaultAvatar() {
        return avatarHash == null;
    }

    @Override
    public Optional<PrivateChannel> getPrivateChannel() {
        return Optional.ofNullable(channel);
    }

    @Override
    public CompletableFuture<PrivateChannel> openPrivateChannel() {
        if (channel != null) {
            return CompletableFuture.completedFuture(channel);
        }
        return new RestRequest<PrivateChannel>(api, RestMethod.POST, RestEndpoint.USER_CHANNEL)
                .setBody(JsonNodeFactory.instance.objectNode().put("recipient_id", getId()))
                .execute(result -> getOrCreateChannel(result.getJsonBody()));
    }

    @Override
    public DiscordApi getApi() {
        return api;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public CompletableFuture<Message> sendMessage(
            String content, EmbedBuilder embed, boolean tts, String nonce, InputStream stream, String fileName) {
        return openPrivateChannel().thenApplyAsync(
                channel -> channel.sendMessage(content, embed, tts, nonce, stream, fileName).join(),
                api.getThreadPool().getExecutorService()
        );
    }

    @Override
    public String toString() {
        return String.format("User (id: %s, name: %s)", getId(), getName());
    }

}
