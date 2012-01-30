/*
 * ArgonMS MapleStory server emulator written in Java
 * Copyright (C) 2011-2012  GoldenKevin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package argonms.game.net.external.handler;

import argonms.common.UserPrivileges;
import argonms.common.character.BuddyList;
import argonms.common.character.KeyBinding;
import argonms.common.character.Skills;
import argonms.common.net.external.ClientSendOps;
import argonms.common.net.external.CommonPackets;
import argonms.common.net.external.RemoteClient;
import argonms.common.util.Rng;
import argonms.common.util.TimeTool;
import argonms.common.util.input.LittleEndianReader;
import argonms.common.util.output.LittleEndianByteArrayWriter;
import argonms.game.GameServer;
import argonms.game.character.GameCharacter;
import argonms.game.character.SkillMacro;
import argonms.game.character.SkillTools;
import argonms.game.net.WorldChannel;
import argonms.game.net.external.GameClient;
import argonms.game.net.external.GamePackets;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

/**
 *
 * @author GoldenKevin
 */
public class GameEnterHandler {
	public static void handlePlayerConnection(LittleEndianReader packet, GameClient gc) {
		int cid = packet.readInt();
		GameCharacter player = null;
		player = GameCharacter.loadPlayer(gc, cid);
		if (player == null)
			return;
		gc.setPlayer(player);
		boolean allowLogin;
		byte state = gc.getOnlineState();
		//TODO: check every game server of this world to see if we are logged in
		//(since a character of a particular world cannot be logged in on any
		//other world). Remember to check local process and remote processes.
		allowLogin = (state == RemoteClient.STATUS_MIGRATION);
		if (!allowLogin) {
			gc.getSession().close("Player " + player.getName() + " tried to double login on world " + gc.getWorld(), null);
			return;
		}
		gc.updateState(RemoteClient.STATUS_INGAME);

		WorldChannel cserv = GameServer.getChannel(gc.getChannel());
		cserv.addPlayer(player);
		gc.getSession().send(writeEnterMap(player));
		//TODO: although shop server is not interchannel, we still have to keep
		//track of the PlayerContext in shop so that non-expired buffs from
		//before we entered the shop could be applied
		boolean firstLogIn = !cserv.applyBuffsFromLastChannel(player);
		if (player.isVisible() && player.getPrivilegeLevel() > UserPrivileges.USER) //hide
			SkillTools.useCastSkill(player, Skills.HIDE, (byte) 1, (byte) -1);
		player.getMap().spawnPlayer(player);

		//TODO: although shop server is not interchannel, we need to display our
		//buddies that are online in the shop server
		BuddyList bList = player.getBuddyList();
		if (firstLogIn) {
			gc.getSession().send(GamePackets.writeBuddyList(BuddyListHandler.ADD, bList));
			for (Entry<Integer, String> invite : bList.getInvites())
				gc.getSession().send(GamePackets.writeBuddyInvite(invite.getKey().intValue(), invite.getValue()));
		}
		cserv.getInterChannelInterface().sendBuddyOnline(player);
		/*if (player.getParty() != null)
			cserv.getInterChannelInterface().updateParty(player, PartyOperation.LOG_ONOFF);
		if (player.getGuildId() > 0) {
			cserv.getInterChannelInterface().setGuildMemberOnline(
					player.getMGC(), true, gc.getChannel());
			gc.getSession().send(GamePackets.writeGuildInfo(player));
		}
		player.updatePartyMemberHP();

		c.getPlayer().showNote();*/
		gc.getSession().send(writeKeymap(player.getKeyMap()));
		gc.getSession().send(writeMacros(player.getMacros()));

		/*player.checkMessenger();
		player.checkBerserk();
		player.itemExpireTask();*/
	}

	private static byte[] writeEnterMap(GameCharacter p) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter();

		lew.writeShort(ClientSendOps.CHANGE_MAP);
		lew.writeInt(p.getClient().getChannel() - 1);
		lew.writeByte((byte) 1);
		lew.writeByte((byte) 1);
		lew.writeShort((short) 0);
		Random generator = Rng.getGenerator();
		lew.writeInt(generator.nextInt());
		lew.writeInt(generator.nextInt());
		lew.writeInt(generator.nextInt());

		CommonPackets.writeCharData(lew, p);

		lew.writeLong(TimeTool.unixToWindowsTime(System.currentTimeMillis()));
		return lew.getBytes();
	}

	private static byte[] writeKeymap(KeyBinding[] bindings) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter(453);

		lew.writeShort(ClientSendOps.KEYMAP);
		lew.writeByte((byte) 0);

		for (int i = 0; i < 90; i++) {
			KeyBinding binding = bindings[i];
			if (binding != null) {
				lew.writeByte(binding.getType());
				lew.writeInt(binding.getAction());
			} else {
				lew.writeByte((byte) 0);
				lew.writeInt(0);
			}
		}
		return lew.getBytes();
	}

	private static byte[] writeMacros(List<SkillMacro> macros) {
		LittleEndianByteArrayWriter lew = new LittleEndianByteArrayWriter();

		lew.writeShort(ClientSendOps.SKILL_MACRO);
		lew.writeByte((byte) macros.size()); // number of macros
		for (SkillMacro macro : macros) {
			lew.writeLengthPrefixedString(macro.getName());
			lew.writeBool(macro.shout());
			lew.writeInt(macro.getFirstSkill());
			lew.writeInt(macro.getSecondSkill());
			lew.writeInt(macro.getThirdSkill());
		}

		return lew.getBytes();
	}
}
