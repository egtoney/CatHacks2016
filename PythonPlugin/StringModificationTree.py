ADDITION = 1
DELETION = 2

class StringModificationTree(object):
	def __init__(self, start_time, base_string):
		self.root_node = StringNode(start_time, 0, base_string)

	def toString(self):
		simplify()
		return self.root_node.data

	def applyTransformation(self, node):
		curr_node = self.root_node

		#Find the last single modification moment in the tree
		while len(curr_node.children) > 0:
			if len(curr_node.children) == 1:
				if curr_node.children[0].mod_time <= node.mod_time:
					curr_node = curr_node.children[0]
				else:
					node.children.append(curr_node.children[0])
					del curr_node.children[:]
					break
			else:
				break

		curr_node.children.append(node)

	def simplify(self):
		while len(self.root_node.children) != 0:
			if len(self.root_node.children) == 1:
				self.root_node = update(self.root_node, self.root_node.children[0])
				break
			else:
				self.root_node = merge(self.root_node, self.root_node.children)
				break

	def merge(self, base_node, children):
		result = base_node

		for i in range(0, len(children)):
			result = update(result, children[i])

			for j in range(i, len(children)):
				children[j].applyTransform(children[i])

		return result

	def update(self, base_node, mod_node):
		result_string = base_node.data

		if mod_node.mod_type == ADDITION:
			result_string = result_string[0:mod_node.mod_loc] 
			+ mod_node.data + result_string[mod_node.mod_loc:]
			
		elif mod_node.mod_type == DELETION:
			result_string = result_string[0:mod_node.mod_loc]
			+ result_string[mod_node.mod_loc + mod_node.length:]
			
		
		new_node = StringNode(mod_node.mod_time, 0, result_string)
		new_node.children = mod_node.children

		return new_node

class StringNode(object):
	mod_type = 0
	length = 0
	children = []

	def __init__(self, mod_time, mod_val, new_val):
		self.mod_time = mod_time
		self.data = new_val
		self.mod_loc = mod_val

		self.mod_type = ADDITION

	def __init__(self, mod_time, mod_val, length, haha):
		self.mod_time = mod_time
		self.length = length

		self.mod_type = DELETION

	def compareTo(self, o1):
		self.o1_t = o1.mod_time

		if o1_t == self.mod_time:
			return 0
		elif self.mod_time < o1_t:
			return -1
		else:
			return 1

	def applyTransform(self, node):
		a = node.mod_loc
		b = a + node.length
		c = self.mod_loc
		d = c + self.mod_loc
		if node.mod_type == ADDITION:					# first operation is ADDITION	
			if self.mod_type == ADDITION:					# second operation is ADDITION done
				if b <= c:
					self.mod_loc += node.length
			else											# second operation is DELETION done
				if b <= c:
					self.mod_loc -= node.length
				elif a >= c && a < d:
					self.length += node.length
		else:											# first operation is DELETION
			if self.mod_type == ADDITION:					# second operation is ADDITION 
				if b <= c:
					self.mod_loc -= node.length
				elif a < c:
					self.mod_loc -= c - a
			else											# second operation is DELETION done
				if b <= c:
					self.mod_loc -= node.length
				elif a <= c:
					if b < d:
						self.mod_loc -= c - a
						self.length -= b - c
					else:
						self.length = 0
				elif a < d:
					if b < d:
						self.length -= b - a
					else:
						self.length -= d - a


