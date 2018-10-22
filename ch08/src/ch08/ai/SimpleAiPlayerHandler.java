package ch08.ai;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import java.io.*;
import ch08.console.ChessConsole;
import ch08.logic.ChessGame;
import ch08.logic.IPlayerHandler;
import ch08.logic.Move;
import ch08.logic.MoveValidator;
import ch08.logic.Piece;

public class SimpleAiPlayerHandler implements IPlayerHandler,java.io.Serializable {

	private ChessGame chessGame;
	private MoveValidator validator;
	Connection myConn;
	Statement stmt;
	static final String WRITE_OBJECT_SQL = "INSERT INTO BOARD(boardstate, object_value) VALUES (?, ?)";
	static final String READ_OBJECT_SQL = "SELECT object_value FROM BOARD WHERE boardstate = ?";
	/**
	 * number of moves to look into the future
	 */
	public int maxDepth = 4;
	public String prevboard="";

	/*MY CODE==================================*/
	
	public static long writeJavaObject(Connection conn,String str, Object object) throws Exception {
	    String className = object.getClass().getName();
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutput out = null;
	    byte[] byteArray=null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(object);

			byteArray = bos.toByteArray();

		} catch (IOException e) {
			e.printStackTrace();
			byteArray=null;

		} finally {
			try {
				if (out != null)
					out.close();
			} catch (IOException ex) {
			}
			try {
				bos.close();
			} catch (IOException ex) {
			}
		}

       
	    PreparedStatement pstmt = conn.prepareStatement(WRITE_OBJECT_SQL);
	    pstmt.setString(1, str);
	    pstmt.setObject(2, byteArray);
	    pstmt.executeUpdate();

	    // get the generated key for the id
	    ResultSet rs = pstmt.getGeneratedKeys();
	    int id = -1;
	    if (rs.next()) {
	      id = rs.getInt(1);
	    }

	    rs.close();
	    pstmt.close();
	    System.out.println("writeJavaObject: done serializing: " + className);
	    return id;
	  }
	public static Object readJavaObject(Connection conn, String str) throws Exception {
	    PreparedStatement pstmt = conn.prepareStatement(READ_OBJECT_SQL);
	    pstmt.setString(1, str);
	    ResultSet rs = pstmt.executeQuery();
	    rs.next();
	    Object object = rs.getObject(1);
	    String className = object.getClass().getName();
	    rs.close();
	    pstmt.close();
	    System.out.println("readJavaObject: done de-serializing: " + className);
	    return object;
	  }
	
	/*MY CODE==================================*/
	
	
	public SimpleAiPlayerHandler(ChessGame chessGame) {
		this.chessGame = chessGame;
		this.validator = this.chessGame.getMoveValidator();
		try
	       {
	           Class.forName("com.mysql.jdbc.Driver").newInstance();
	           myConn=DriverManager.getConnection("jdbc:mysql://localhost:3306/javatest","root","");
	           stmt=myConn.createStatement();
	           //myConn.setAutoCommit(false);
	       }
	       catch(SQLException e)
	       {
	           System.out.println(e);   
	       }
	       catch(Exception e)
	       {
	           System.out.println(e);
	       }
	}

	@Override
	public Move getMove() {
		return getBestMove();
	}
	private int getcount(String board) {
		ResultSet rs;
		int count=0;
		try {
			rs = stmt.executeQuery("SELECT * FROM BOARD WHERE boardstate='"+board+"';");
		
        while(rs.next())
        {
            count++;
        }
        } catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return count;
	}
	private Move getBestMove() {
		System.out.println("getting best move");
		ChessConsole.printCurrentGameState(this.chessGame);
		//System.out.println("thinking...");	
		List<Move> validMoves = generateMoves(false);
		int bestResult = Integer.MIN_VALUE;
		int flag=0;
		Move bestMove = null;
		String board=boardstate();
		prevboard=board;
		if(getcount(board)==0) {
		//**********************************************System.out.println(boardstate());
		for (Move move : validMoves) {
			//System.out.println(move);
			executeMove(move);
			//System.out.println("evaluate move: "+move+" =========================================");
			int evaluationResult = -1 * negaMax(this.maxDepth,"");
			//System.out.println("result: "+evaluationResult);
			undoMove(move);
			if( evaluationResult > bestResult){
				bestResult = evaluationResult;
				bestMove = move;
			}
			write(board,bestMove.score,bestMove.sourceColumn,bestMove.sourceRow,bestMove.targetColumn,bestMove.targetRow);
		}
		}
		else {
		bestMove=read(board);
		}
		System.out.println("Move Done!");
		//System.out.println(bestMove.score+bestMove.sourceColumn+bestMove.sourceRow+bestMove.targetColumn+bestMove.targetRow);
		return bestMove;
	}
	public void write(String board,int score, int sc,int sr,int tc,int tr) {
		if(getcount(board)>0) {
			String update="UPDATE BOARD SET sc="+sc+",sr="+sr+",score="+score+",tr="+tr+",tc="+tc+" WHERE boardstate='"+board+"';";
			try {
				stmt.executeUpdate(update);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			String insert="INSERT INTO BOARD VALUES('"+board+"',"+score+","+sc+","+sr+","+tc+","+tr+")";
			try {
				stmt.executeUpdate(insert);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	public Move read(String board) {
		Move move=new Move(0, 0,0,0);
		try {
			ResultSet rs=stmt.executeQuery("SELECT * FROM BOARD WHERE boardstate='"+board+"';");
			//System.out.println(board);
			while(rs.next()&& rs!=null) {
			int x=rs.getInt(2);
			move.sourceColumn=rs.getInt(3);
			move.sourceRow=rs.getInt(4);
			move.targetColumn=rs.getInt(5);
			move.targetRow=rs.getInt(6);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			System.out.println("Error");
			e.printStackTrace();
		}
		return move;
	}
	@Override
	public void moveSuccessfullyExecuted(Move move) {
		// we are using the same chessGame instance, so no need to do anything here.
		//System.out.println("executed: "+move);
	}

	/**
	 * evaluate current game state according to nega max algorithm
	 *
	 * @param depth - current depth level (number of counter moves that still need to be evaluated)
	 * @param indent - debug string, that is placed in front of each log message
	 * @return integer score of game state after looking at "depth" counter moves
	 */
	private int negaMax(int depth, String indent) {

		if (depth <= 0
			|| this.chessGame.getGameState() == ChessGame.GAME_STATE_END_WHITE_WON
			|| this.chessGame.getGameState() == ChessGame.GAME_STATE_END_BLACK_WON){
			
			return evaluateState();
		}
		
		List<Move> moves = generateMoves(false);
		int currentMax = Integer.MIN_VALUE;
		for(Move currentMove : moves){
			
			executeMove(currentMove);
			//ChessConsole.printCurrentGameState(this.chessGame);
			int score = -1 * negaMax(depth - 1, indent+" ");
			//System.out.println(indent+"handling move: "+currentMove+" : "+score);
			undoMove(currentMove);
			
			if( score > currentMax){
				currentMax = score;
			}
		}
		//System.out.println(indent+"max: "+currentMax);
		return currentMax;
	}

	/**
	 * undo specified move
	 */
	private void undoMove(Move move) {
		//System.out.println("undoing move");
		this.chessGame.undoMove(move);
		//state.changeGameState();
	}

	/**
	 * Execute specified move. This will also change the game state after the
	 * move has been executed.
	 */
	private void executeMove(Move move) {
		//System.out.println("executing move");
		this.chessGame.movePiece(move);
		this.chessGame.changeGameState();
	}

	/**
	* generate all possible/valid moves for the specified game
	* @param state - game state for which the moves should be generated
	* @return list of all possible/valid moves
	*/
	
	private String boardstate() {
		Piece sourcePiece;
		String str="";
		for(int i=0;i<8;i++){
			int count=0;
		    for(int j=0;j<8;j++){
		        sourcePiece = this.chessGame.getNonCapturedPieceAtLocation(i,j);
		        if( sourcePiece == null ){
					count++;
				}
		        else {
		        	if(count>0) {
		        	str+=Integer.toString(count);
		        	count=0;}
				// source piece has right color?
				if( sourcePiece.getColor() == Piece.COLOR_WHITE){
					switch (sourcePiece.getType()) {
					case Piece.TYPE_BISHOP:
						str+="B";break;
					case Piece.TYPE_KING:
						str+="K";break;
					case Piece.TYPE_KNIGHT:
						str+="H";break;
					case Piece.TYPE_PAWN:
						str+="P";break;
					case Piece.TYPE_QUEEN:
						str+="Q";break;
					case Piece.TYPE_ROOK:
						str+="R";break;
					default: break;
				}
				}else if( sourcePiece.getColor() == Piece.COLOR_BLACK){
					switch (sourcePiece.getType()) {
					case Piece.TYPE_BISHOP:
						str+="b";break;
					case Piece.TYPE_KING:
						str+="k";break;
					case Piece.TYPE_KNIGHT:
						str+="h";break;
					case Piece.TYPE_PAWN:
						str+="p";break;
					case Piece.TYPE_QUEEN:
						str+="q";break;
					case Piece.TYPE_ROOK:
						str+="r";break;
					default: break;
				}
				}
		        }
		    }
		    if(count>0) {
		    	str+=Integer.toString(count);
		    }
		    str+="/";
		}
		return str;
		
	}
	
	private List<Move> generateMoves(boolean debug) {

		List<Piece> pieces = this.chessGame.getPieces();
		List<Move> validMoves = new ArrayList<Move>();
		Move testMove = new Move(0,0,0,0);
		
		int pieceColor = (this.chessGame.getGameState()==ChessGame.GAME_STATE_WHITE
			?Piece.COLOR_WHITE
			:Piece.COLOR_BLACK);	
		
		// iterate over all non-captured pieces
		for (Piece piece : pieces) {

			// only look at pieces of current players color
			if (pieceColor == piece.getColor()) {
				// start generating move
				testMove.sourceRow = piece.getRow();
				testMove.sourceColumn = piece.getColumn();

				Piece sourcePiece;
				
				// iterate over all board rows and columns
				for (int targetRow = Piece.ROW_1; targetRow <= Piece.ROW_8; targetRow++) {
					for (int targetColumn = Piece.COLUMN_A; targetColumn <= Piece.COLUMN_H; targetColumn++) {

						// finish generating move
						testMove.targetRow = targetRow;
						testMove.targetColumn = targetColumn;

						//System.out.println("testing move: "+testMove);
						
						// check if generated move is valid
						if (this.validator.isMoveValid(testMove, true)) {
							// valid move
							validMoves.add(testMove.clone());
						} else {
							// generated move is invalid, so we skip it
						}
					}
				}

			}
		}
		return validMoves;
	}

	/**
	 * evaluate the current game state from the view of the
	 * current player. High numbers indicate a better situation for
	 * the current player.
	 *
	 * @return integer score of current game state
	 */
	private int evaluateState() {

		// add up score
		//
		int scoreWhite = 0;
		int scoreBlack = 0;
		for (Piece piece : this.chessGame.getPieces()) {
			if(piece.getColor() == Piece.COLOR_BLACK){
				scoreBlack +=
					getScoreForPieceType(piece.getType());
				scoreBlack +=
					getScoreForPiecePosition(piece.getRow(),piece.getColumn());
			}else if( piece.getColor() == Piece.COLOR_WHITE){
				scoreWhite +=
					getScoreForPieceType(piece.getType());
				scoreWhite +=
					getScoreForPiecePosition(piece.getRow(),piece.getColumn());
			}else{
				throw new IllegalStateException(
						"unknown piece color found: "+piece.getColor());
			}
		}
		
		// return evaluation result depending on who's turn it is
		int gameState = this.chessGame.getGameState();
		
		if( gameState == ChessGame.GAME_STATE_BLACK){
			return scoreBlack - scoreWhite;
		
		}else if(gameState == ChessGame.GAME_STATE_WHITE){
			return scoreWhite - scoreBlack;
		
		}else if(gameState == ChessGame.GAME_STATE_END_WHITE_WON
				|| gameState == ChessGame.GAME_STATE_END_BLACK_WON){
			return Integer.MIN_VALUE + 1;
		
		}else{
			throw new IllegalStateException("unknown game state: "+gameState);
		}
	}
	
	/**
	 * get the evaluation bonus for the specified position
	 * @param row - one of Piece.ROW_..
	 * @param column - one of Piece.COLUMN_..
	 * @return integer score
	 */
	private int getScoreForPiecePosition(int row, int column) {
		byte[][] positionWeight =
		{ {1,1,1,1,1,1,1,1}
		 ,{2,2,2,2,2,2,2,2}
		 ,{2,2,3,3,3,3,2,2}
		 ,{2,2,3,4,4,3,2,2}
		 ,{2,2,3,4,4,3,2,2}
		 ,{2,2,3,3,3,3,2,2}
		 ,{2,2,2,2,2,2,2,2}
		 ,{1,1,1,1,1,1,1,1}
		 };
		return positionWeight[row][column];
	}

	/**
	 * get the evaluation score for the specified piece type
	 * @param type - one of Piece.TYPE_..
	 * @return integer score
	 */
	private int getScoreForPieceType(int type){
		switch (type) {
			case Piece.TYPE_BISHOP: return 30;
			case Piece.TYPE_KING: return 99999;
			case Piece.TYPE_KNIGHT: return 30;
			case Piece.TYPE_PAWN: return 10;
			case Piece.TYPE_QUEEN: return 90;
			case Piece.TYPE_ROOK: return 50;
			default: throw new IllegalArgumentException("unknown piece type: "+type);
		}
	}

	public static void main(String[] args) {
		ChessGame ch = new ChessGame();
		SimpleAiPlayerHandler ai = new SimpleAiPlayerHandler(ch);
		/*
		ch.pieces = new ArrayList<Piece>();
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_KING, Piece.ROW_3, Piece.COLUMN_C);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_KING, Piece.ROW_4, Piece.COLUMN_C);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_5, Piece.COLUMN_C);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_4, Piece.COLUMN_B);
		ChessConsole.printCurrentGameState(ch);
		System.out.println("score: "+ai.evaluateState());
		System.out.println("move: "+ai.getBestMove()); //c4 b4
		*/
		
		/*
		  a  b  c  d  e  f  g  h  
		  +--+--+--+--+--+--+--+--+
		 8|BR|  |  |  |  |  |  |BR|8
		  +--+--+--+--+--+--+--+--+
		 7|BP|  |WR|  |BK|  |BP|BP|7
		  +--+--+--+--+--+--+--+--+
		 6|  |  |  |  |BP|BP|  |  |6
		  +--+--+--+--+--+--+--+--+
		 5|  |  |  |  |  |  |  |  |5
		  +--+--+--+--+--+--+--+--+
		 4|  |  |  |  |BB|  |  |  |4
		  +--+--+--+--+--+--+--+--+
		 3|  |  |  |  |WB|WP|  |  |3
		  +--+--+--+--+--+--+--+--+
		 2|WP|  |  |WQ|  |  |  |WP|2
		  +--+--+--+--+--+--+--+--+
		 1|  |  |  |  |WK|  |  |WR|1
		  +--+--+--+--+--+--+--+--+
		   a  b  c  d  e  f  g  h
		*/
		
		/*
		ch.pieces = new ArrayList<Piece>();
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_8, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_8, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_KING, Piece.ROW_7, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_G);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_ROOK, Piece.ROW_7, Piece.COLUMN_C);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_6, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_6, Piece.COLUMN_F);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_BISHOP, Piece.ROW_4, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_BISHOP, Piece.ROW_3, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_3, Piece.COLUMN_F);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_2, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_QUEEN, Piece.ROW_2, Piece.COLUMN_D);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_2, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_KING, Piece.ROW_1, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_ROOK, Piece.ROW_1, Piece.COLUMN_H);
		ChessConsole.printCurrentGameState(ch);
		ai = new SimpleAiPlayerHandler(ch);
		System.out.println("score: "+ai.evaluateState());
		System.out.println("move: "+ai.getBestMove()); //c4 b4
		*/
		
		/*
		 *   a  b  c  d  e  f  g  h  
 +--+--+--+--+--+--+--+--+
8|BR|  |  |  |  |  |  |BR|8
 +--+--+--+--+--+--+--+--+
7|BP|BB|WR|  |BK|  |BP|BP|7
 +--+--+--+--+--+--+--+--+
6|  |  |  |  |BP|BP|  |  |6
 +--+--+--+--+--+--+--+--+
5|  |  |  |  |  |  |  |  |5
 +--+--+--+--+--+--+--+--+
4|  |  |  |  |WP|  |  |  |4
 +--+--+--+--+--+--+--+--+
3|  |  |  |  |WB|WP|  |  |3
 +--+--+--+--+--+--+--+--+
2|WP|  |  |WQ|  |  |  |WP|2
 +--+--+--+--+--+--+--+--+
1|  |  |  |  |WK|  |  |WR|1
 +--+--+--+--+--+--+--+--+
  a  b  c  d  e  f  g  h  
		 */
		
		ch.pieces = new ArrayList<Piece>();
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_8, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_ROOK, Piece.ROW_8, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_BISHOP, Piece.ROW_7, Piece.COLUMN_B);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_ROOK, Piece.ROW_7, Piece.COLUMN_C);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_KING, Piece.ROW_7, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_G);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_7, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_6, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_6, Piece.COLUMN_F);
		ch.createAndAddPiece(Piece.COLOR_BLACK, Piece.TYPE_PAWN, Piece.ROW_4, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_BISHOP, Piece.ROW_3, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_3, Piece.COLUMN_F);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_2, Piece.COLUMN_A);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_QUEEN, Piece.ROW_2, Piece.COLUMN_D);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_PAWN, Piece.ROW_2, Piece.COLUMN_H);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_KING, Piece.ROW_1, Piece.COLUMN_E);
		ch.createAndAddPiece(Piece.COLOR_WHITE, Piece.TYPE_ROOK, Piece.ROW_1, Piece.COLUMN_H);
		ch.gameState = ChessGame.GAME_STATE_BLACK;
		ChessConsole.printCurrentGameState(ch);
		//System.out.println("score: "+ai.evaluateState());
		//System.out.println("move: "+ai.getBestMove()); //c4 b4
	}
}
