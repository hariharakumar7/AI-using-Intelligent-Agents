package ch08;
import ch08.ai.SimpleAiPlayerHandler;
import ch08.console.ChessConsole;
import ch08.gui.ChessGui;
import ch08.logic.ChessGame;
import ch08.logic.Piece;


public class Main {

	public static void main(String[] args) {

		// first we create the game
		ChessGame chessGame = new ChessGame();

		// then we create the clients/players
		ChessGui chessGui = new ChessGui(chessGame);
		//ChessConsole chessConsole = new ChessConsole(chessGame);
		SimpleAiPlayerHandler ai1 = new SimpleAiPlayerHandler(chessGame);
		SimpleAiPlayerHandler ai2 = new SimpleAiPlayerHandler(chessGame);

		// set strength of AI
		ai1.maxDepth = 4;
		ai2.maxDepth = 4;

		// then we attach the clients/players to the game
		//chessGame.setPlayer(Piece.COLOR_WHITE, chessGui);
		//chessGame.setPlayer(Piece.COLOR_WHITE, chessConsole);
		chessGame.setPlayer(Piece.COLOR_WHITE, ai2);
		chessGame.setPlayer(Piece.COLOR_BLACK, ai1);
		//chessGame.setPlayer(Piece.COLOR_BLACK, chessGui);
		
		// in the end we start the game
		new Thread(chessGame).start();
	}
	
}