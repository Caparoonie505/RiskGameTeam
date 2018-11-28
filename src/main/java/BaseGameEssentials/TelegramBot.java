package BaseGameEssentials;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import static BaseGameEssentials.Game.*;


public class TelegramBot extends TelegramLongPollingBot {

	private static String botKeyStr = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
	private BufferedReader br;
	private FileReader fr;
	public static ArrayList<String> temp = new ArrayList<String>();
	public static String message_text = new String();
	public int i = 0;
	public boolean gameOn = false;
	public boolean AttackPhase = false;
	public static Territory AttackingTerr, DefendingTerr;
	public static boolean WIN = false;
	public static boolean NotEnoughTroops = false;
	public Player PLAYERTurnKeeper;
	public static Update update2;

	public TelegramBot(){
		//Get TelegramBot credentials
		readInCredentials("/src/main/java/BaseGameEssentials/telegramBotToken");
	}

	@Override
	/**
	 * receiving updates from the bot, directing the updates to their responsive methods.
	 * @param Update
	 * **/
	public void onUpdateReceived(Update update) {

		// receiving commands
		update2 = update;
		if (update2.hasCallbackQuery()){ //If the update is call back query of a button
             ParsQuery();
		}else {//If updates are regular commands
			String command = update.getMessage().getText();
			System.out.println(command);
			if (command.equals("/start") || command.equals("/join@CSGSanaz_bot") || command.equals("/attack@CSGSanaz_bot")) {
				translateMessage(command);
			}
		}
	}

	@Override
	public String getBotUsername() {
		// Return bot username
		return "CSGSanaz_bot";
	}

	@Override
	public String getBotToken() {
		// Return bot token from BotFather
		return botKeyStr;
	}


	/**Method to Translate the initial commands(/start , /join) and to take actions respectively
	 * On /start creates the first player and sends welcome message
	 * On /join allows creation of the two other players
	 * On presence of the needed number of player, territories get distributed and reinforced with one troop
	 * through calling TelegramTerritoryDistribution(), and List of territories are printed back on telegram chat by calling the
	 * method printTerritories()
	 * @Param input which is the string received from the bot
	 * **/
	public void translateMessage(String input) {

		if (input.equals("/start")) {
			String playerName = update2.getMessage().getFrom().getFirstName();
			playerList.add(new Player(35, playerName, false, false, (i)));
			i++;
			message_text = "Welcome To Risk " + playerName + " you are our first player. We need two more players to play the game." +
					"If you want to play send /join@CSGSanaz_bot " + "command to become a player.";
			Send(message_text);
		}

		if (input.equals("/join@CSGSanaz_bot") && playerList.size() != 3) {
			String playerName = update2.getMessage().getFrom().getFirstName();
			i++;
			boolean goodToGo = false;
			//ensuring the same player does not enroll to the game more than once
			if (playerList.size() == 1 && !playerName.equals(playerList.get(0).getTeam())) {
				goodToGo = true;
			}
			if (goodToGo == true) {
				playerList.add(new Player(35, playerName, false, false, (i)));
				message_text = playerName + " you are a Risk player now .";
				Send(message_text);
				if (playerList.size() == 2) {
					message_text = " Dividing territories among players, Reinforcement will begin soon .";
					Send(message_text);
					TelegramTerritoryDistribution(); // Distribute territories among players
					for(Player p: playerList) {
						int ID=0;
						for (Territory t :territoryList ){
							if(t.getTeam().equals(p.getTeam())) {
								TelegramReinforce(p.getTeam(), t.getID());
							}
					    }
					}

				}

			}
		}
		if(input.equals("/attack@CSGSanaz_bot") && AttackPhase==true && gameOn==true) {
			TurnKeeper(playerList);
			ArrayList <Territory> AttackFrom = new ArrayList();
			AttackFrom = FindWhereToAttackFrom(PLAYERTurnKeeper);
			printWhatTerritories(PLAYERTurnKeeper, "from#", AttackFrom);
		}
	}

	/** prints the territories that a given player can attack from as buttons to choose from
	 * Calls the method FindWhereToAttackFrom(P) to find the eligible territories to attack from
	 * Setting the call back data from each button to be the name of the country, attached to "from#"
	 * @param P: a given player
	 * **/
	public void printWhatTerritories(Player P, String direction, ArrayList<Territory> List){
		    long chat_id = update2.getMessage().getChatId();
		    for(int i=0; i < List.size();i++){
				SendMessage message = new SendMessage() // Create a message object object
						.setChatId(chat_id)
						.setText(P.getTeam()+" You may choose the following");
				InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
				List<List<InlineKeyboardButton>> rowsInline = new ArrayList();
				List<InlineKeyboardButton> rowInline = new ArrayList();
				rowInline.add(new InlineKeyboardButton().setText(List.get(i).getName()).setCallbackData(direction+List.get(i).getName()));
				// Set the keyboard to the markup
				rowsInline.add(rowInline);
				// Add it to the message
				markupInline.setKeyboard(rowsInline);
				message.setReplyMarkup(markupInline);
				try {
					execute(message); // Sending our message object to user
				} catch (TelegramApiException e) {
					e.printStackTrace();
				}
			}

		}

	/**
	 * Method to parse the call back query results
	 * Once a button is pushed , the bot sends a callbackquery and once our onUpdateReceived() method catches the callbackquery,
	 *  it will call ParseQuery() which parses the call back data
	 *  If call back data starts with from, we call FindWhereICanAttack() then we pass the returned arrayList to PrintWhereICanAttack()
	 *  If call back data starts with to, we get the territory to be attacked then call the fulFilAttack() method
	 *  On WIN==true, allows the winner to transfer troops to the new territory .
	 *  On NotEnoughTroops == true, stops the attack, gives turn to the next player.
	 *  **/
	public void ParsQuery() {
		temp.clear();
		String call_data = update2.getCallbackQuery().getData();
		String[] tokens = call_data.split("#");
		for (String t : tokens) {
			temp.add(t);
		}
		if(temp.get(0).equals("from")){
			ArrayList<Territory> GoodToAttack = new ArrayList();
			GoodToAttack = FindWhereICanAttack(temp.get(1));
			printWhatTerritories(PLAYERTurnKeeper,"to",GoodToAttack);
		}else if(temp.get(0).equals("to")){
			for(Territory t: territoryList){
				if(t.getName().equals(temp.get(1))){DefendingTerr=t; System.out.println(DefendingTerr.getTeam());}
			}
            fulfillAttack(playerList.get(0), DefendingTerr, AttackingTerr);

            if (WIN == true) {
               	int RemainingTroops = AttackingTerr.getTroopCount()- 1;
				AttackingTerr.setTroopCount(RemainingTroops);
				DefendingTerr.setTroopCount(1);
				System.out.println("Troop movement was successful.");
				Send(AttackingTerr.getTeam() + " you have won. You have placed 1 troop on"+ DefendingTerr.getName());

            }
            if (NotEnoughTroops == true) {
                String Mess = "You don't have enough troops to continue this attack. Wait for your turn and " +
                        "choose another territory to attack from ";
				Send(Mess);
            }
		}
	}

    /** General Method to send Message to telegram chat
	 * @param mess : String message to be sent
	 * **/
    public void Send(String mess){
        long chat_id = update2.getMessage().getChatId();
        SendMessage messageX = new SendMessage() // Create a message object object
                .setChatId(chat_id)
                .setText(mess);
        try {
            execute(messageX); // Sending our message object to user
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
	 * Method to keep track of players turns
	 * @param List : playerList
	 * **/
    public void TurnKeeper(ArrayList<Player> List){
    	if(List.get(0).getTurn()== true && List.get(1).getTurn()==true && List.get(2).getTurn()==true){
    		for(Player p: playerList ){ p.SetTurn(false);}
		}
		for(Player P: playerList){
			if(P.getTurn() == false){
				P.SetTurn(true);
				PLAYERTurnKeeper = P;
				break;
			}
		}
    }

	//Read in BotToken
	private void readInCredentials(String filepath){
		try{
			File currentDir = new File(".");
			File parentDir = currentDir.getAbsoluteFile();
			File newFile = new File(parentDir + filepath);
			fr = new FileReader(newFile);
			br = new BufferedReader(fr);
			String line = null;
			int count = 0;
			while((line = br.readLine()) != null){
				if(count == 0){
					botKeyStr = line;
				}
				count++;
			}
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			try{
				if(br != null){
					br.close();
				}
				if(fr != null){
					fr.close();
				}
			}catch(IOException ex){
				ex.printStackTrace();
			}
		}
	}

}
