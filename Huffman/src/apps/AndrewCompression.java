package apps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import models.Unsigned8BitModel;
import codec.HuffmanEncoder;
import codec.SymbolDecoder;
import codec.SymbolEncoder;
import models.Symbol;
import models.SymbolModel;
import models.Unsigned8BitModel.Unsigned8BitSymbol;
import io.InsufficientBitsLeftException;
import io.BitSink;
import io.BitSource;
import codec.ArithmeticDecoder;
import codec.ArithmeticEncoder;
import codec.HuffmanDecoder;
import io.InputStreamBitSource;
import io.OutputStreamBitSink;

public class AndrewCompression {
	
	public static void main(String[] args) throws IOException, InsufficientBitsLeftException {
		String[] names = {"bunny", "candle", "jellyfish", "pinwheel", "tractor"};
		for(String x : names) {
			compress(x);
		}
	}
	
	public static void compress(String name) throws IOException, InsufficientBitsLeftException {
		String base = name;
		String filename="C:\\Users\\Andrew Whang\\Desktop\\DataCompression\\" + base + ".450p.yuv";
		File file = new File(filename);
		int width = 800;
		int height = 450;
		int num_frames = 150;


		Unsigned8BitModel model = new Unsigned8BitModel();

		InputStream training_values = new FileInputStream(file);
		
		int[][][] diff = new int[num_frames][width][height];
		int[][] current_frame = new int[width][height];

		for (int f=0; f < num_frames; f++) {
			System.out.println("Training frame difference " + f);
			int[][] prior_frame = current_frame;
			current_frame = readFrame(training_values, width, height);
			//System.out.println(current_frame[0][0]);
			int[][] diff_frame = frameDifference(prior_frame, current_frame);
			diff[f] = diff_frame;
			trainModelWithFrame(model, diff_frame);
		}
		training_values.close();
		// System.out.print(diff[0][0][0]);
		//java.lang.System.exit(1);
		HuffmanEncoder encoder = new HuffmanEncoder(model, model.getCountTotal());
		Map<Symbol, String> code_map = encoder.getCodeMap();

		// SymbolEncoder encoder = new ArithmeticEncoder(model);

		Symbol[] symbols = new Unsigned8BitSymbol[256];
		for (int v=0; v<256; v++) {
			SymbolModel s = model.getByIndex(v);
			Symbol sym = s.getSymbol();
			symbols[v] = sym;

			long prob = s.getProbability(model.getCountTotal());
			System.out.println("Symbol: " + sym + " probability: " + prob + "/" + model.getCountTotal());
		}			

		InputStream message = new FileInputStream(file);

		File out_file = new File("C:\\Users\\Andrew Whang\\Desktop\\DataCompression\\" + base + "-compressed.dat");
		OutputStream out_stream = new FileOutputStream(out_file);
		BitSink bit_sink = new OutputStreamBitSink(out_stream);

		int w = 0;
		int h = 0;
		int frame_index;
		int current_pixel;
		while(w < width && h < height) {
			frame_index = 0;
			while(frame_index < num_frames) {
				current_pixel = diff[frame_index][w][h];
				int dupe_frames = 1;
				while(frame_index + dupe_frames < num_frames) {
					if(diff[frame_index + dupe_frames][w][h] != current_pixel) {
						break;
					}
					dupe_frames++;
				}
				System.out.println(w+","+h+":"+frame_index+","+dupe_frames);
				encoder.encode(symbols[current_pixel], bit_sink);
				encoder.encode(symbols[dupe_frames], bit_sink);
				frame_index += dupe_frames;
			}
			if(w+1 == width) {
				h++;
			}
			w = (w+1) % width;
		}
		message.close();
		encoder.close(bit_sink);
		out_stream.close();
		
		BitSource bit_source = new InputStreamBitSource(new FileInputStream(out_file));
		OutputStream decoded_file = new FileOutputStream(new File("C:\\Users\\Andrew Whang\\Desktop\\DataCompression\\" + base + "-decoded.dat"));

		SymbolDecoder decoder = new HuffmanDecoder(code_map);
		// SymbolDecoder decoder = new ArithmeticDecoder(model);

		w = 0;
		h = 0;
		int[][][] uncompressed = new int[num_frames][width][height];
		while(w < width && h < height) {
			frame_index = 0;
			while(frame_index < num_frames) {
				int sym_diff = ((Unsigned8BitSymbol) decoder.decode(bit_source)).getValue();
				int duplicate = ((Unsigned8BitSymbol) decoder.decode(bit_source)).getValue();
				for(int i = 0; i < duplicate; i++) {
					if(frame_index == 0) {
						uncompressed[frame_index+i][w][h] = sym_diff;
					} else {
						uncompressed[frame_index+i][w][h] = (uncompressed[frame_index+i-1][w][h]+sym_diff)%256;
					}
				}
				frame_index += duplicate;
			}

			if(w+1 == width) {
				h++;
			}
			w = (w+1) % width;
		}
		for(int i = 0; i < num_frames; i++) {
			outputFrame(uncompressed[i], decoded_file);
		}
		
//		for (int f=0; f<num_frames; f++) {
//			System.out.println("Decoding frame " + f);
//			int[][] prior_frame = current_frame;
//			int[][] diff_frame = decodeFrame(decoder, bit_source, width, height);
//			current_frame = reconstructFrame(prior_frame, diff_frame);
//			outputFrame(current_frame, decoded_file);
//		}

		decoded_file.close();

	}

	private static int[][] readFrame(InputStream src, int width, int height) 
			throws IOException {
		int[][] frame_data = new int[width][height];
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				frame_data[x][y] = src.read();
			}
		}
		return frame_data;
	}

	private static int[][] frameDifference(int[][] prior_frame, int[][] current_frame) {
		int width = prior_frame.length;
		int height = prior_frame[0].length;

		int[][] difference_frame = new int[width][height];

		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				difference_frame[x][y] = ((current_frame[x][y] - prior_frame[x][y])+256)%256;
			}
		}
		return difference_frame;
	}

	private static void trainModelWithFrame(Unsigned8BitModel model, int[][] frame) {
		int width = frame.length;
		int height = frame[0].length;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				model.train(frame[x][y]);
			}
		}
	}

	private static void encodeFrameDifference(int[][] frame, SymbolEncoder encoder, BitSink bit_sink, Symbol[] symbols) 
			throws IOException {

		int width = frame.length;
		int height = frame[0].length;

		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				encoder.encode(symbols[frame[x][y]], bit_sink);
			}
		}
	}

	private static int[][] decodeFrame(SymbolDecoder decoder, BitSource bit_source, int width, int height) 
			throws InsufficientBitsLeftException, IOException {
		int[][] frame = new int[width][height];
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				frame[x][y] = ((Unsigned8BitSymbol) decoder.decode(bit_source)).getValue();
			}
		}
		return frame;
	}

	private static int[][] reconstructFrame(int[][] prior_frame, int[][] frame_difference) {
		int width = prior_frame.length;
		int height = prior_frame[0].length;

		int[][] frame = new int[width][height];
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				frame[x][y] = (prior_frame[x][y] + frame_difference[x][y])%256;
			}
		}
		return frame;
	}

	private static void outputFrame(int[][] frame, OutputStream out) 
			throws IOException {
		int width = frame.length;
		int height = frame[0].length;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				out.write(frame[x][y]);
			}
		}
	}
}
