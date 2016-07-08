package net.citringo.slackcraftforge;

import com.google.common.io.Resources;
import com.ullink.slack.simpleslackapi.SlackAttachment;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackChatConfiguration;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Mod(modid = SlackCraftForge.MODID, version = SlackCraftForge.VERSION)
public class SlackCraftForge
{
    public static final String MODID = "slackcraftforge";
    public static final String VERSION = "1.0";

	public SlackChannel logChannel;

	public SlackSession slackSession;

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
	    MinecraftForge.EVENT_BUS.register(this);

    }

	@EventHandler
	public void serverStarting(FMLServerStartingEvent e)
	{

		try
		{
			slackSession = SlackSessionFactory.createWebSocketSlackSession(Resources.toString(Resources.getResource("key"), Charset.forName("UTF-8")));
			slackSession.connect();
			for (SlackChannel c : slackSession.getChannels())
			{
				if (c.getName().equals("gamelog"))
				{
					logChannel = c;
				}
			}
			slackSession.addMessagePostedListener(new SlackMessagePostedListener()
			{
				@Override
				public void onEvent(SlackMessagePosted event, SlackSession session)
				{
					if (event.getSender().isBot())
						return;
					String name = event.getSender().getUserName();
					if (event.getSlackFile() == null)
					{
						MinecraftServer.getServer().addChatMessage(new ChatComponentText(
								"[" +
										EnumChatFormatting.GREEN +
										"Slack" +
										EnumChatFormatting.RESET +
										"]" +
										name +
										EnumChatFormatting.GREEN +
										": " +
										EnumChatFormatting.RESET +
										event.getMessageContent()));
					}
					else
					{
						String type = event.getSlackFile().getFiletype();
						String title = event.getSlackFile().getTitle();
						String url = event.getSlackFile().getUrl();
						MinecraftServer.getServer().addChatMessage(new ChatComponentText(
								"[" +
										EnumChatFormatting.GREEN  +
										"Slack" +
										EnumChatFormatting.RESET +
										"]" +
										type +
										"形式のファイル「" +
										EnumChatFormatting.AQUA +
										title +
										EnumChatFormatting.RESET +
										"」が、" +
										EnumChatFormatting.GOLD +
										name +
										EnumChatFormatting.RESET +
										"によって共有されました．表示するにはこのテキストをクリックしてください")
								.setChatStyle(new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
										.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(url)))));
					}
				}
			});

		} catch (IOException e1)
		{
			e1.printStackTrace();
		}
	}


	@EventHandler
	public void serverStopping(FMLServerStoppingEvent e)
	{
		if (slackSession != null)
			try
			{
				slackSession.disconnect();
			} catch (IOException e1)
			{
				e1.printStackTrace();
			}
	}

	@NetworkCheckHandler
	public boolean netCheckHandler(Map<String, String> mods, Side side)
	{
		return true;
	}

	public String getPlayerName(EntityPlayer p) {
		String s = p.getDisplayNameString();
		//if (MinecraftServer.getServer().getPluginManager().getPlugin("Vault") != null && chat != null) {
		//	s = chat.getPlayerPrefix(p) + s;
		//	s += chat.getPlayerSuffix(p);
		//}
		return s;
	}


	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onServerChat(ServerChatEvent e)
	{
		logWrite(e.player, e.message);
	}

	private void logWrite(EntityPlayer player, String text)
	{
		text = text.replaceAll("§.", "");
		if (player != null) {
			String name = getPlayerName(player);
			name = name.replaceAll("§.", "").replaceAll("&.", "");
			slackSession.sendMessage(logChannel, text, null, SlackChatConfiguration.getConfiguration().withName(name).withIcon("https://mcapi.ca/avatar/3d/" + player.getName() + "/48/48"));
		} else {
			slackSession.sendMessage(logChannel, text, null, SlackChatConfiguration.getConfiguration().withName("サーバー"));

		}
	}

	@SubscribeEvent
	public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e)
	{
		logWrite(e.player.getDisplayNameString() + "が参加しました");
	}

	@SubscribeEvent
	public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent e)
	{
		logWrite(e.player.getDisplayNameString() + "が退出しました");
	}

	@SubscribeEvent
	public void onPlayerDeath(LivingDeathEvent e)
	{
		if (e.entityLiving instanceof EntityPlayer)
		{
			BlockPos l = e.entity.getPosition();
			logWrite((EntityPlayer)e.entityLiving, String.format("%s 内の(%d, %d, %d) で死んでしまいました: \n%s", e.entity.getEntityWorld().getProviderName(), (int) l.getX(), (int) l.getY(), (int) l.getZ(), e.source.getDamageType()));
		}
	}

	@SubscribeEvent
	public void onPlayerAchievement(AchievementEvent e)
	{
		logWrite(e.entityPlayer, String.format("%s を獲得しました！", e.achievement.getStatName().getUnformattedText()));
	}

	private void logWrite(String s)
	{
		logWrite(null, s);
	}


}
