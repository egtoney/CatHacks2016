package Server;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StringModificationTree {
	
	public static void main( String[] args ){
		StringModificationTree string = new StringModificationTree( System.currentTimeMillis()-1000, "" );
		
		string.applyTransformation( new StringNode( System.currentTimeMillis(), 0, "Hello World" ) );
		string.applyTransformation( new StringNode( System.currentTimeMillis()-500, 0, "a" ) );
		
		
		StringNode curr_node = string.root_node;
		// Find the last single modification moment in the tree
		while( curr_node.children.size() > 0 ){
			curr_node = curr_node.children.get(0);
		}
		
		string.simplify();
		
		System.out.println( string.root_node.data );
	}
	
	public static final int ADDITION = 1;
	public static final int DELETION = 2;
	
	StringNode root_node;
	
	public StringModificationTree( long start_time, String base_string ){
		root_node = new StringNode( start_time, 0, base_string );
	}
	
	public String toString(){
		simplify();
		return root_node.data;
	}
	
	public void applyTransformation( StringNode node ){
		StringNode curr_node = root_node;
		
		// Find the last single modification moment in the tree
		while( curr_node.children.size() > 0 ){
			if( curr_node.children.size() == 1 ){
				if( curr_node.children.get(0).mod_time <= node.mod_time ){
					curr_node = curr_node.children.get(0);
				}else{
					node.children.add(curr_node.children.get(0));
					curr_node.children.clear();
					break;
				}
			}else{
				break;
			}
		}
		
		curr_node.children.add(node);
	}
	
	private void simplify(){
		// While the root node has children
		while( !root_node.children.isEmpty() ){
			// If there is only one child
			switch( root_node.children.size() ){
			case( 1 ): // Add the one child node to the parent node
				root_node = update( root_node, root_node.children.get(0) );	
				break;
			
			default: // merge the two or more children nodes
				root_node = merge( root_node, root_node.children );
				break;
			}
		}
	}
	
	private StringNode merge(StringNode base_node, List<StringNode> children) {
		Collections.sort(children);
		StringNode result = base_node;
		
		for( int i=0 ; i<children.size() ; i++ ){
			// Apply this transform
			result = update( result, children.get(i) );
			
			// Adjust all other transforms relative
			for( int j=i ; j<children.size() ; j++ ){
				children.get(j).applyTransform( children.get(i) );
			}
		}
		
		return result;
	}

	private StringNode update(StringNode base_node, StringNode mod_node) {
		String result_string = base_node.data;
		
		switch( mod_node.mod_type ){
		case( ADDITION ):
			result_string = result_string.substring( 0, mod_node.mod_loc ) + mod_node.data + result_string.substring( mod_node.mod_loc );
			break;
		
		case( DELETION ):
			result_string = result_string.substring( 0, mod_node.mod_loc ) + result_string.substring( mod_node.mod_loc + mod_node.length );
			break;
		}
		
		StringNode new_node = new StringNode( mod_node.mod_time, 0, result_string );
		new_node.children = mod_node.children;
		
		return new_node;
	}

	public static class StringNode implements Comparable<StringNode> {
		
		public int mod_type = 0;
		public long mod_time;
		public int mod_loc;
		public int length = 0;
        public String data;
        public StringNode parent = null;
        public List< StringNode > children = new LinkedList<>();
		
		public StringNode( long mod_time, int mod_val, String new_val ) {
			this.mod_time = mod_time;
			data = new_val;
			mod_loc = mod_val;
			
			mod_type = ADDITION;
		}
		
		/**
		 * Apply transform node before this transform
		 * @param node
		 */
		public void applyTransform(StringNode node) {
			int a = mod_loc;
			int b = a + length;
			int c = node.mod_loc;
			int d = c + node.mod_loc;
			
			if( b < c ){ // to the left of node
				// do nothing
				
			}else if( b < d ){
				if( a > c ){ // inside of node
					mod_loc += node.length;
					
				}else{ // left overlap with node
					// do nothing
					
				}
			}else if( a < d ){ // right overlap with node
				mod_loc += node.length;
				
			}else{ // to the right of node
				mod_loc += node.length;
				
			}
		}

		public StringNode( long mod_time, int mod_val, int length ) {
			this.mod_time = mod_time;
			this.length = length;
			
			mod_type = DELETION;
		}

		@Override
		public int compareTo(StringNode o1) {
			long o1_t = o1.mod_time;
			
			if( o1_t == mod_time ){
				return 0;
			}else if( mod_time < o1_t ){
				return -1;
			}else{
				return 1;
			}
		}
		
	}
	
}
